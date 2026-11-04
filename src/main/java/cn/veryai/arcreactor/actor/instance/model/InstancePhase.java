package cn.veryai.arcreactor.actor.instance.model;

public enum InstancePhase {
    NEW,
    PREPARING_DEPENDENCIES,
    PREPARING_SECURITY_GROUP,
    PREPARING_NETWORK,
    PREPARING_FORWARDING,
    SCHEDULING,
    PROVISIONING,
    CONFIRMING,
    ACTIVE,
    COMPENSATING,
    FAILED
}
