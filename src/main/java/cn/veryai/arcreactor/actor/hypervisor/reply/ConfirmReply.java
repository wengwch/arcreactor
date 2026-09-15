package cn.veryai.arcreactor.actor.hypervisor.reply;

import java.io.Serializable;

public record ConfirmReply(boolean success, boolean idempotent, String detail) implements Serializable {
    public static ConfirmReply succeeded(boolean idempotent) { return new ConfirmReply(true, idempotent, "confirmed"); }
    public static ConfirmReply rejected(String detail) { return new ConfirmReply(false, false, detail); }
}
