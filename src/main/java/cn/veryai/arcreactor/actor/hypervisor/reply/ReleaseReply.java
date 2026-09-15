package cn.veryai.arcreactor.actor.hypervisor.reply;

import java.io.Serializable;

public record ReleaseReply(boolean success, boolean idempotent) implements Serializable {
    public static ReleaseReply succeeded(boolean idempotent) { return new ReleaseReply(true, idempotent); }
}
