package cn.veryai.arcreactor.actor.instance.model;

public enum InstancePhase {
    NEW,
    SCHEDULING,
    RESERVING_RESOURCES,
    PREPARING_DEPENDENCIES,
    PREPARING_SECURITY_GROUP,
    PREPARING_NETWORK,
    PREPARING_FORWARDING,
    PROVISIONING,
    CONFIRMING,
    ACTIVE,
    REBOOTING,
    REBUILDING,
    DELETING_NOVA,
    DELETING_FORWARDINGS,
    DELETING_NETWORK,
    DELETING_SECURITY_GROUP,
    CANCELLING_RESERVATION,
    RELEASING_ALLOCATION,
    DELETED,
    DELETE_FAILED,
    FAILED;

    /** Cleanup is in progress; DELETE_FAILED waits for a new deletion request. */
    public boolean isDeleting() {
        return switch (this) {
            case DELETING_NOVA, DELETING_FORWARDINGS, DELETING_NETWORK,
                    DELETING_SECURITY_GROUP, CANCELLING_RESERVATION, RELEASING_ALLOCATION -> true;
            default -> false;
        };
    }
}
