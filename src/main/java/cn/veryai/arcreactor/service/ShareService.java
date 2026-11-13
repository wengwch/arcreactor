package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.user.UserResources;
import cn.veryai.arcreactor.actor.user.model.ShareKind;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.vo.ShareView;
import cn.veryai.arcreactor.web.params.CreateShareParam;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.openstack4j.model.manila.Share;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShareService {
    private static final Duration PROJECT_PREPARATION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration PROJECT_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Executor EXECUTOR = task -> Thread.ofVirtual().name("user-share-creation").start(task);

    private final UserResources userResources;
    private final RegionRepo regionRepo;
    private final OpenstackClusterRepo clusterRepo;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletionStage<ShareView> create(String userId, CreateShareParam param) {
        requireId(userId);
        RegionEntity region = regionRepo.findById(param.getRegionId());
        if (region == null) throw new BaseException(ErrorCode.REGION_NOT_FOUND);
        if (!region.isEnabled()) throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE);
        if (clusterRepo.findById(region.getClusterId()) == null) {
            throw new BaseException(ErrorCode.OPENSTACK_CLUSTER_NOT_FOUND);
        }
        ShareSpec requested;
        try {
            requested = new ShareSpec(region.getId(), null, param.getSize(), Share.Protocol.CEPHFS,
                    param.getShareType(), ShareKind.USER);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BaseException(ErrorCode.BAD_REQUEST, exception);
        }
        return CompletableFuture.supplyAsync(() -> prepareProject(userId, region.getClusterId()), EXECUTOR)
                .thenCompose(projectId -> userResources.createShare(userId, requested.withOsProjectId(projectId)))
                .handle((reply, error) -> {
                    if (error != null) throw operationFailure(error);
                    return view(userId, reply);
                });
    }

    public CompletionStage<ShareView> get(String userId, String shareId) {
        requireId(userId);
        requireId(shareId);
        return userResources.getShare(userId, shareId).handle((reply, error) -> {
            if (error != null) throw operationFailure(error);
            return view(userId, reply);
        });
    }

    private String prepareProject(String userId, String clusterId) {
        long deadline = System.nanoTime() + PROJECT_PREPARATION_TIMEOUT.toNanos();
        boolean create = true;
        try {
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new BaseException(ErrorCode.GATEWAY_TIMEOUT);
                try {
                    ProjectReply reply = (create
                            ? userResources.createProject(userId, clusterId, userId)
                            : userResources.getProject(userId, clusterId))
                            .toCompletableFuture().get(remaining, TimeUnit.NANOSECONDS);
                    if (reply == null) throw new BaseException(ErrorCode.BAD_GATEWAY);
                    switch (reply.status()) {
                        case READY -> {
                            if (!userId.equals(reply.userId()) || !clusterId.equals(reply.clusterId())
                                    || reply.osProjectId() == null || reply.osProjectId().isBlank()) {
                                throw new BaseException(ErrorCode.BAD_GATEWAY);
                            }
                            return reply.osProjectId();
                        }
                        case NOT_FOUND -> create = true;
                        case IN_PROGRESS -> create = false;
                        case REJECTED -> throw new BaseException(ErrorCode.CONFLICT);
                        case FAILED -> throw new BaseException(ErrorCode.BAD_GATEWAY);
                    }
                } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                    Throwable cause = rootCause(exception);
                    if (!(cause instanceof TimeoutException)) throw operationFailure(cause);
                    // An ask timeout does not confirm absence; query before submitting another intent.
                    create = false;
                }
                remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new BaseException(ErrorCode.GATEWAY_TIMEOUT);
                TimeUnit.NANOSECONDS.sleep(Math.min(PROJECT_POLL_INTERVAL.toNanos(), remaining));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, exception);
        }
    }

    private static ShareView view(String userId, ShareReply reply) {
        if (reply == null) throw new BaseException(ErrorCode.BAD_GATEWAY);
        if (reply.status() == ShareReply.Status.NOT_FOUND) throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        if (reply.status() == ShareReply.Status.REJECTED) throw new BaseException(ErrorCode.CONFLICT);
        if (reply.share() == null) throw new BaseException(ErrorCode.BAD_GATEWAY);
        return ShareView.from(userId, reply.share());
    }

    private static BaseException operationFailure(Throwable error) {
        Throwable cause = rootCause(error);
        if (cause instanceof BaseException business) return business;
        return new BaseException(cause instanceof TimeoutException
                ? ErrorCode.GATEWAY_TIMEOUT : ErrorCode.BAD_GATEWAY, cause);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static void requireId(String value) {
        if (value == null || value.isBlank() || value.length() > 255) throw new BaseException(ErrorCode.BAD_REQUEST);
    }
}
