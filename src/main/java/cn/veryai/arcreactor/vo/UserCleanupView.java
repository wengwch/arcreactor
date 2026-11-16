package cn.veryai.arcreactor.vo;

import cn.veryai.arcreactor.actor.user.reply.UserCleanupReply;
import java.io.Serializable;

/** Public progress of the user's resource cleanup. */
public record UserCleanupView(
        String userId,
        String phase,
        String currentResourceId,
        int remainingNetworks,
        int remainingShares,
        int remainingProjects,
        String failureMessage) implements Serializable {

    public static UserCleanupView from(UserCleanupReply reply) {
        var cleanup = reply.cleanup();
        return new UserCleanupView(reply.userId(), cleanup == null ? "IDLE" : cleanup.phase().name(),
                reply.currentResourceId(), reply.remainingNetworks(), reply.remainingShares(), reply.remainingProjects(),
                cleanup == null ? null : cleanup.failureMessage());
    }
}
