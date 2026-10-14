package cn.veryai.arcreactor.actor.instance.model;

public enum InstancePhase {
    NEW,
    PREPARING_SHARE,
    PREPARING_FORWARDING,
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
