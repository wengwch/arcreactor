package cn.veryai.arcreactor.actor.instance.event;

/** Pins the preset SHARED network and subnet before the parent port create intent is persisted. */
public record SharedNetworkPrepared(String instanceId, String networkId, String subnetId)
    implements InstanceWorkflowEvent {}
