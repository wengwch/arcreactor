package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.instance.model.NetworkRequirement;
import cn.veryai.arcreactor.actor.instance.model.PreparedDependencies;
import cn.veryai.arcreactor.actor.instance.model.PreparedShare;
import cn.veryai.arcreactor.actor.instance.model.ShareRequirement;
import cn.veryai.arcreactor.actor.user.UserResources;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.model.UserNetworkState;
import cn.veryai.arcreactor.actor.user.reply.NetworkReply;
import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import cn.veryai.arcreactor.config.InstanceCreationProperties;
import cn.veryai.arcreactor.entity.SysNetworkEntity;
import cn.veryai.arcreactor.enums.SysNetworkType;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.repo.SysNetworkRepo;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.pattern.AskTimeoutException;
import org.springframework.stereotype.Service;

/** Asynchronous workflow dependency preparation; reusable resources remain owned by UserActor. */
@Service
@RequiredArgsConstructor
public class InstanceDependencyPreparer {
    private static final Executor EXECUTOR = task -> Thread.ofVirtual().name("instance-dependencies").start(task);
    private final UserResources userResources;
    private final OpenStackClient openStackClient;
    private final SysNetworkRepo sysNetworkRepo;
    private final InstanceCreationProperties properties;

    public Instant preparationDeadline(Instant startedAt) {
        Duration timeout = properties.getDependencyPreparationTimeout();
        requirePositive(timeout);
        return startedAt.plus(timeout);
    }

    public CompletionStage<PreparedDependencies> prepare(String userId, String clusterId, String shareId,
            ShareSpec shareSpec, NetworkRequirement network, Instant deadline) {
        Duration pollInterval = properties.getDependencyPollInterval();
        requirePositive(pollInterval);
        long remaining = remainingNanos(deadline);
        return CompletableFuture.supplyAsync(() -> {
            try {
                String region = shareSpec.regionId();
                var project = awaitReady(() -> userResources.createProject(userId, clusterId, userId),
                        () -> userResources.getProject(userId, clusterId),
                        reply -> reply.status() == ProjectReply.Status.READY,
                        reply -> reply.status() == ProjectReply.Status.IN_PROGRESS,
                        reply -> reply.status() == ProjectReply.Status.NOT_FOUND,
                        deadline, pollInterval, ErrorCode.INSTANCE_PROJECT_PREPARATION_FAILED);
                if (project == null || project.status() != ProjectReply.Status.READY
                        || !userId.equals(project.userId()) || !clusterId.equals(project.clusterId())
                        || !hasText(project.projectId())) {
                    throw new BaseException(ErrorCode.INSTANCE_PROJECT_PREPARATION_FAILED);
                }
                String projectId = project.projectId();
                NetworkRequirement resolved = resolveSharedNetwork(region, projectId, network);
                ShareSpec resolvedShare = shareSpec.withProjectId(projectId);
                var shareReply = awaitReady(() -> userResources.createShare(userId, shareId, resolvedShare),
                        () -> userResources.getShare(userId, region),
                        reply -> reply.status() == ShareReply.Status.READY,
                        reply -> reply.status() == ShareReply.Status.IN_PROGRESS,
                        reply -> reply.status() == ShareReply.Status.NOT_FOUND,
                        deadline, pollInterval, ErrorCode.INSTANCE_SHARE_PREPARATION_FAILED);
                if (shareReply == null || shareReply.status() != ShareReply.Status.READY) {
                    throw new BaseException(ErrorCode.INSTANCE_SHARE_PREPARATION_FAILED);
                }
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
                var networkReply = awaitReady(() -> userResources.createNetwork(userId, clusterId, network.userNetwork()),
                        () -> userResources.getNetwork(userId, region),
                        reply -> reply.status() == NetworkReply.Status.READY,
                        reply -> reply.status() == NetworkReply.Status.IN_PROGRESS,
                        reply -> reply.status() == NetworkReply.Status.NOT_FOUND,
                        deadline, pollInterval, ErrorCode.INSTANCE_USER_NETWORK_PREPARATION_FAILED);
                if (networkReply == null || networkReply.status() != NetworkReply.Status.READY) {
                    throw new BaseException(ErrorCode.INSTANCE_USER_NETWORK_PREPARATION_FAILED);
                }
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
                        new ShareRequirement(userId, share.shareId(), resolvedShare), preparedShare,
                        resolved.withSubNetwork(readyNetwork.networkId(), readyNetwork.subnetId(),
                                readyNetwork.localNetworkId()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BaseException(ErrorCode.INSTANCE_DEPENDENCY_PREPARATION_INTERRUPTED, exception);
            }
        }, EXECUTOR).orTimeout(remaining, TimeUnit.NANOSECONDS)
                .exceptionally(error -> {
                    Throwable cause = error;
                    while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                    if (cause instanceof TimeoutException) throw new BaseException(ErrorCode.INSTANCE_DEPENDENCY_PREPARATION_TIMEOUT, cause);
                    if (cause instanceof RuntimeException runtime) throw runtime;
                    throw new CompletionException(cause);
                });
    }

    /** Re-submitting an intent only returns existing state; all subsequent waiting uses queries. */
    private <T> T awaitReady(Supplier<CompletionStage<T>> create, Supplier<CompletionStage<T>> query,
            Predicate<T> ready, Predicate<T> inProgress, Predicate<T> notFound,
            Instant deadline, Duration pollInterval, ErrorCode failure) throws InterruptedException {
        Supplier<CompletionStage<T>> request = create;
        while (true) {
            try {
                T reply = awaitReply(request, deadline, failure);
                if (reply == null) throw new BaseException(failure);
                if (ready.test(reply)) return reply;
                if (notFound.test(reply)) request = create;
                else if (inProgress.test(reply)) request = query;
                else throw new BaseException(failure);
            } catch (AskTimeoutException exception) {
                // A reply may be lost during entity recovery; query before submitting another intent.
                request = query;
            }
            TimeUnit.NANOSECONDS.sleep(Math.min(pollInterval.toNanos(), remainingNanos(deadline)));
        }
    }

    private <T> T awaitReply(Supplier<CompletionStage<T>> ask, Instant deadline, ErrorCode failure)
            throws InterruptedException, AskTimeoutException {
        long remaining = remainingNanos(deadline);
        try {
            return ask.get().toCompletableFuture().get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            throw new BaseException(ErrorCode.INSTANCE_DEPENDENCY_PREPARATION_TIMEOUT, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof AskTimeoutException timeout) throw timeout;
            throw new BaseException(failure, cause);
        } catch (RuntimeException exception) {
            throw new BaseException(failure, exception);
        }
    }

    private static long remainingNanos(Instant deadline) {
        long remaining = Duration.between(Instant.now(), deadline).toNanos();
        if (remaining <= 0) throw new BaseException(ErrorCode.INSTANCE_DEPENDENCY_PREPARATION_TIMEOUT);
        return remaining;
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
