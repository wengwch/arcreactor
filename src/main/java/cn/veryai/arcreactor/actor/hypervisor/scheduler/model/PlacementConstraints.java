package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import java.io.Serializable;
import org.apache.commons.lang3.StringUtils;

public record PlacementConstraints(String availabilityZone, String regionId, String hypervisorType, String hostname)
        implements Serializable {
    public static final PlacementConstraints NONE = new PlacementConstraints(null, null, null, null);

    public PlacementConstraints {
        hostname = StringUtils.trimToNull(hostname);
    }
}
