package cn.veryai.arcreactor.actor.lease.reply;

import cn.veryai.arcreactor.actor.lease.model.LeaseState;

import java.io.Serializable;

public record LeaseReply(boolean accepted, String error, LeaseState state) implements Serializable {
    public static LeaseReply accepted(LeaseState state) { return new LeaseReply(true, null, state); }
    public static LeaseReply rejected(String error, LeaseState state) { return new LeaseReply(false, error, state); }
}
