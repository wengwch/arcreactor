package cn.veryai.arcreactor.actor.instance.reply;

import java.io.Serializable;

public record InstanceReply(boolean accepted, boolean idempotent, String detail) implements Serializable {
    public static InstanceReply accepted(boolean idempotent) {
        return new InstanceReply(true, idempotent, "instance workflow accepted");
    }

    public static InstanceReply rejected(String detail) {
        return new InstanceReply(false, false, detail);
    }
}
