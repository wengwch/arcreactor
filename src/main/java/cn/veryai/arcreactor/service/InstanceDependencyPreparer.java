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

    public CompletionStage<PreparedDependencies> prepare(String userId, String clusterId,
            ShareSpec shareSpec, NetworkRequirement network) {
        Duration pollInterval = properties.getDependencyPollInterval();
        requirePositive(pollInterval);
        return CompletableFuture.supplyAsync(() -> {
            try {
                String region = shareSpec.regionId();
                var project = awaitReady(() -> userResources.createProject(userId, clusterId, userId),
                        () -> userResources.getProject(userId, clusterId),
                        reply -> reply.status() == ProjectReply.Status.READY,
                        reply -> reply.status() == ProjectReply.Status.IN_PROGRESS,
                        reply -> reply.status() == ProjectReply.Status.NOT_FOUND,
                        pollInterval, ErrorCode.INSTANCE_PROJECT_PREPARATION_FAILED);
                if (project == null || project.status() != ProjectReply.Status.READY
                        || !userId.equals(project.userId()) || !clusterId.equals(project.clusterId())
                        || !hasText(project.osProjectId())) {
                    throw new BaseException(ErrorCode.INSTANCE_PROJECT_PREPARATION_FAILED);
                }
                String osProjectId = project.osProjectId();
                NetworkRequirement resolved = resolveSharedNetwork(region, network);
                ShareSpec resolvedShare = shareSpec.withOsProjectId(osProjectId);
                var shareReply = awaitReady(() -> userResources.createShare(userId, resolvedShare),
                        () -> userResources.getShare(userId, region),
                        reply -> reply.status() == ShareReply.Status.READY,
                        reply -> reply.status() == ShareReply.Status.IN_PROGRESS,
                        reply -> reply.status() == ShareReply.Status.NOT_FOUND,
                        pollInterval, ErrorCode.INSTANCE_SHARE_PREPARATION_FAILED);
                if (shareReply == null || shareReply.status() != ShareReply.Status.READY) {
                    throw new BaseException(ErrorCode.INSTANCE_SHARE_PREPARATION_FAILED);
                }
                var share = shareReply.share();
                if (share == null || !resolvedShare.equals(share.spec()) || !hasText(share.shareId())) {
                    throw new BaseException(ErrorCode.INSTANCE_SHARE_PREPARATION_FAILED);
                }
                PreparedShare preparedShare;
                try {
                    preparedShare = PreparedShare.from(userId, share);
                } catch (IllegalArgumentException exception) {
                    throw new BaseException(ErrorCode.INSTANCE_SHARE_PREPARATION_FAILED, exception);
                }
                var networkReply = awaitReady(() -> userResources.createNetwork(userId, clusterId, network.userNetwork()),
                        () -> userResources.getNetwork(userId, region),
                        reply -> reply.status() == NetworkReply.Status.READY,
                        reply -> reply.status() == NetworkReply.Status.IN_PROGRESS,
                        reply -> reply.status() == NetworkReply.Status.NOT_FOUND,
                        pollInterval, ErrorCode.INSTANCE_USER_NETWORK_PREPARATION_FAILED);
                if (networkReply == null || networkReply.status() != NetworkReply.Status.READY) {
                    throw new BaseException(ErrorCode.INSTANCE_USER_NETWORK_PREPARATION_FAILED);
                }
                UserNetworkState readyNetwork = networkReply.network();
                if (!userId.equals(networkReply.userId()) || !region.equals(networkReply.regionId())
                        || readyNetwork == null || readyNetwork.phase() != UserNetworkState.Phase.READY
                        || !network.userNetwork().equals(readyNetwork.spec()) || !osProjectId.equals(readyNetwork.osProjectId())
                        || !hasText(readyNetwork.osNetworkId()) || !hasText(readyNetwork.osSubnetId())
                        || !hasText(readyNetwork.osRouterId()) || !hasText(readyNetwork.osLocalNetworkId())
                        || !hasText(readyNetwork.osRouterPortId())) {
                    throw new BaseException(ErrorCode.INSTANCE_USER_NETWORK_PREPARATION_FAILED);
                }
                return new PreparedDependencies(osProjectId,
                        new ShareRequirement(userId, share.shareId(), resolvedShare), preparedShare,
                        resolved.withSubNetwork(readyNetwork.osNetworkId(), readyNetwork.osSubnetId(),
                                readyNetwork.osLocalNetworkId()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BaseException(ErrorCode.INSTANCE_DEPENDENCY_PREPARATION_INTERRUPTED, exception);
            }
        }, EXECUTOR);
    }

    /** Submit the creation intent once, then query until ready; re-submit only when absent. */
    private <T> T awaitReady(Supplier<CompletionStage<T>> create, Supplier<CompletionStage<T>> query,
            Predicate<T> ready, Predicate<T> inProgress, Predicate<T> notFound,
            Duration pollInterval, ErrorCode failure) throws InterruptedException {
        Supplier<CompletionStage<T>> request = create;
        while (true) {
            try {
                T reply = awaitReply(request, failure);
                if (reply == null) throw new BaseException(failure);
                if (ready.test(reply)) return reply;
                if (notFound.test(reply)) request = create;
                else if (inProgress.test(reply)) request = query;
                else throw new BaseException(failure);
            } catch (TimeoutException exception) {
                // A reply may be lost during entity recovery; query before submitting another intent.
                request = query;
            }
            TimeUnit.NANOSECONDS.sleep(pollInterval.toNanos());
        }
    }

    private <T> T awaitReply(Supplier<CompletionStage<T>> ask, ErrorCode failure)
            throws InterruptedException, TimeoutException {
        try {
            return ask.get().toCompletableFuture().get();
        } catch (ExecutionException | RuntimeException exception) {
            Throwable cause = exception;
            while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                    && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof TimeoutException timeout) throw timeout;
            throw new BaseException(failure, cause);
        }
    }

    private NetworkRequirement resolveSharedNetwork(String region, NetworkRequirement requested) {
        try {
            List<SysNetworkEntity> matches = sysNetworkRepo.findByRegionId(region).stream()
                    .filter(network -> network.getSysNetworkType() == SysNetworkType.SHARED).toList();
            if (matches.size() != 1) throw new BaseException(ErrorCode.INSTANCE_SHARED_NETWORK_INVALID);
            SysNetworkEntity preset = matches.getFirst();
            if (!preset.isShared() || !hasText(preset.getOsNetId()) || !hasText(preset.getOsSubNetId())) {
                throw new BaseException(ErrorCode.INSTANCE_SHARED_NETWORK_INVALID);
            }
            var network = openStackClient.getNetwork(region, preset.getOsProjectId(), preset.getOsNetId());
            var subnet = openStackClient.getSubnet(region, preset.getOsProjectId(), preset.getOsSubNetId());
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
