package cn.veryai.arcreactor.actor.hypervisor;

/** Trait names used to constrain instance placement to its OpenStack location. */
public final class PlacementTraits {
    private PlacementTraits() {}

    public static String cluster(String clusterId) {
        return "cluster:" + clusterId;
    }

    public static String region(String region) {
        return "region:" + region;
    }
}
