package cn.veryai.arcreactor.actor.hypervisor.reply;

import java.io.Serializable;

public record ActionReply(boolean success, boolean idempotent, String detail) implements Serializable {
    public static ActionReply succeeded(boolean idempotent) { return new ActionReply(true, idempotent, "ok"); }
    public static ActionReply rejected(String detail) { return new ActionReply(false, false, detail); }
}
