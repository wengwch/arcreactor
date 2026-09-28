package cn.veryai.arcreactor.actor.instance.model;

public enum InstancePhase {
    NEW,
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
