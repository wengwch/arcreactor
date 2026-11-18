package cn.veryai.arcreactor.actor.instance.model;

public record ForwardingDeletionTarget(String claimId, String floatingIpId, int internalPort) {
  public static ForwardingDeletionTarget from(PreparedForwarding forwarding) {
    return new ForwardingDeletionTarget(forwarding.claimId(), forwarding.floatingIpId(), forwarding.internalPort());
  }
}
