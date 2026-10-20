package cn.veryai.arcreactor.actor.instance.event;

/** Pins the user-owned network before any instance ports are created. */
public record UserNetworkPrepared(String instanceId, String networkId, String subnetId)
    implements InstanceWorkflowEvent {}
