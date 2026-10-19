package cn.veryai.arcreactor.actor.instance.model;

public enum InstancePhase {
    NEW,
    PREPARING_PROJECT,
    PREPARING_SHARE,
    PREPARING_FORWARDING,
    PREPARING_NETWORK,
    SCHEDULING,
    RESERVING,
    RESERVED,
    NOVA_CREATING,
    BUILDING,
    RECONCILING,
    CONFIRMING,
    ACTIVE,
    COMPENSATING,
    FAILED,
    DELETING,
    DELETED
}
