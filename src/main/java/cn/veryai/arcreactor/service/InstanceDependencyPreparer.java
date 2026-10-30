package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.instance.model.NetworkRequirement;
import cn.veryai.arcreactor.actor.instance.model.PreparedShare;
import cn.veryai.arcreactor.actor.instance.model.ShareRequirement;
import cn.veryai.arcreactor.actor.user.UserResources;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.model.UserNetworkState;
import cn.veryai.arcreactor.config.InstanceCreationProperties;
import cn.veryai.arcreactor.entity.SysNetworkEntity;
import cn.veryai.arcreactor.enums.SysNetworkType;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.repo.SysNetworkRepo;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** API orchestration only; reusable resources remain owned and recovered by UserActor. */
@Service
@RequiredArgsConstructor
public class InstanceDependencyPreparer {
    private static final Executor EXECUTOR = task -> Thread.ofVirtual().name("instance-dependencies").start(task);
    private final UserResources userResources;
    private final OpenStackClient openStackClient;
    private final SysNetworkRepo sysNetworkRepo;
    private final InstanceCreationProperties properties;

    public record PreparedDependencies(String projectId, ShareRequirement share, NetworkRequirement network) {}

    public CompletionStage<PreparedDependencies> prepare(String userId, String clusterId, String shareId,
            ShareSpec shareSpec, NetworkRequirement network) {
        requirePositive(properties.getDependencyPreparationTimeout());
        requirePositive(properties.getDependencyPollInterval());
        return CompletableFuture.supplyAsync(() -> {
            long deadline = System.nanoTime() + properties.getDependencyPreparationTimeout().toNanos();
            try {
                String region = shareSpec.regionId();
                var project = awaitReady(() -> userResources.getProject(userId, clusterId),
                        () -> userResources.createProject(userId, clusterId, userId), reply -> reply.status().name(),
                        deadline, ErrorCode.INSTANCE_PROJECT_PREPARATION_FAILED);
                if (!userId.equals(project.userId()) || !clusterId.equals(project.clusterId()) || !hasText(project.projectId())) {
                    throw new BaseException(ErrorCode.INSTANCE_PROJECT_PREPARATION_FAILED);
                }
                String projectId = project.projectId();
                NetworkRequirement resolved = resolveSharedNetwork(region, projectId, network);
                ShareSpec resolvedShare = shareSpec.withProjectId(projectId);
                var shareReply = awaitReady(() -> userResources.getShare(userId, region),
                        () -> userResources.createShare(userId, shareId, resolvedShare), reply -> reply.status().name(),
                        deadline, ErrorCode.INSTANCE_SHARE_PREPARATION_FAILED);
                var share = shareReply.share();
                if (share == null || !resolvedShare.equals(share.spec()) || !hasText(share.shareId())) {
                    throw new BaseException(ErrorCode.INSTANCE_SHARE_PREPARATION_FAILED);
                }
                PreparedShare preparedShare;
                try {
                    preparedShare = PreparedShare.from(share);
                } catch (IllegalArgumentException exception) {
                    throw new BaseException(ErrorCode.INSTANCE_SHARE_PREPARATION_FAILED, exception);
                }
                var networkReply = awaitReady(() -> userResources.getNetwork(userId, region),
                        () -> userResources.createNetwork(userId, clusterId, network.userNetwork()), reply -> reply.status().name(),
                        deadline, ErrorCode.INSTANCE_USER_NETWORK_PREPARATION_FAILED);
                UserNetworkState readyNetwork = networkReply.network();
                if (!userId.equals(networkReply.userId()) || !region.equals(networkReply.regionId())
                        || readyNetwork == null || readyNetwork.phase() != UserNetworkState.Phase.READY
                        || !network.userNetwork().equals(readyNetwork.spec()) || !projectId.equals(readyNetwork.projectId())
                        || !hasText(readyNetwork.networkId()) || !hasText(readyNetwork.subnetId())
                        || !hasText(readyNetwork.routerId()) || !hasText(readyNetwork.localNetworkId())
                        || !hasText(readyNetwork.routerPortId())) {
                    throw new BaseException(ErrorCode.INSTANCE_USER_NETWORK_PREPARATION_FAILED);
                }
                return new PreparedDependencies(projectId,
                        new ShareRequirement(userId, share.shareId(), resolvedShare, preparedShare),
                        resolved.withSubNetwork(readyNetwork.networkId(), readyNetwork.subnetId()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BaseException(ErrorCode.INSTANCE_DEPENDENCY_PREPARATION_INTERRUPTED, exception);
            }
        }, EXECUTOR).orTimeout(properties.getDependencyPreparationTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(error -> {
                    Throwable cause = error;
                    while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                    if (cause instanceof TimeoutException) throw new BaseException(ErrorCode.INSTANCE_DEPENDENCY_PREPARATION_TIMEOUT, cause);
                    if (cause instanceof RuntimeException runtime) throw runtime;
                    throw new CompletionException(cause);
                });
    }

    private <T> T awaitReady(Supplier<CompletionStage<T>> query, Supplier<CompletionStage<T>> create,
            Function<T, String> status, long deadline, ErrorCode failure) throws InterruptedException {
        boolean submit = false;
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new BaseException(ErrorCode.INSTANCE_DEPENDENCY_PREPARATION_TIMEOUT);
            T reply = null;
            try {
                reply = (submit ? create.get() : query.get()).toCompletableFuture().get(remaining, TimeUnit.NANOSECONDS);
            } catch (ExecutionException | TimeoutException ignored) {
                // A lost ask reply may follow a successful write. Query before any resubmission.
            }
            if (reply != null) {
                switch (status.apply(reply)) {
                    case "READY" -> { return reply; }
                    case "FAILED", "REJECTED" -> throw new BaseException(failure);
                    case "NOT_FOUND" -> {
                        if (!submit) { submit = true; continue; }
                    }
                    case "IN_PROGRESS" -> { }
                    default -> throw new BaseException(failure);
                }
            }
            submit = false;
            remaining = deadline - System.nanoTime();
            if (remaining > 0) TimeUnit.NANOSECONDS.sleep(Math.min(remaining, properties.getDependencyPollInterval().toNanos()));
        }
    }

    private NetworkRequirement resolveSharedNetwork(String region, String projectId, NetworkRequirement requested) {
        try {
            List<SysNetworkEntity> matches = sysNetworkRepo.findByRegionId(region).stream()
                    .filter(network -> network.getSysNetworkType() == SysNetworkType.SHARED).toList();
            if (matches.size() != 1) throw new BaseException(ErrorCode.INSTANCE_SHARED_NETWORK_INVALID);
            SysNetworkEntity preset = matches.getFirst();
            if (!preset.isShared() || !hasText(preset.getOsNetId()) || !hasText(preset.getOsSubNetId())) {
                throw new BaseException(ErrorCode.INSTANCE_SHARED_NETWORK_INVALID);
            }
            var network = openStackClient.getNetwork(region, projectId, preset.getOsNetId());
            var subnet = openStackClient.getSubnet(region, projectId, preset.getOsSubNetId());
            if (network == null || !network.isShared() || !preset.getOsNetId().equals(network.getId())
                    || subnet == null || !preset.getOsSubNetId().equals(subnet.getId())
                    || !network.getId().equals(subnet.getNetworkId())) {
                throw new BaseException(ErrorCode.INSTANCE_SHARED_NETWORK_INVALID);
            }
            return requested.withParentNetwork(network.getId(), subnet.getId());
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BaseException(ErrorCode.INSTANCE_SHARED_NETWORK_INVALID, exception);
        }
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static void requirePositive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) throw new BaseException(ErrorCode.INSTANCE_CREATION_CONFIG_INVALID);
    }
}
