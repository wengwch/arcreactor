package cn.veryai.arcreactor.actor.instance.model;

public enum InstancePhase {
    NEW,
    PREPARING_DEPENDENCIES,
    PREPARING_SECURITY_GROUP,
    PREPARING_PARENT_PORT,
    PREPARING_SUB_PORT,
    PREPARING_TRUNK,
    PREPARING_FORWARDING,
    SCHEDULING,
    PROVISIONING,
    CONFIRMING,
    ACTIVE,
    DELETING,
    DELETED,
    FAILED;

    public boolean isPreparingNetwork() {
        return this == PREPARING_PARENT_PORT || this == PREPARING_SUB_PORT || this == PREPARING_TRUNK;
    }
}
