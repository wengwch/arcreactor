package cn.veryai.arcreactor.config;

import cn.veryai.arcreactor.actor.instance.model.PortBandwidthLimit;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Server-side defaults for the dependencies of a newly submitted instance workflow. */
@Data
@Component
@ConfigurationProperties(prefix = "instance.creation")
public class InstanceCreationProperties {
    private Duration dependencyPreparationTimeout = Duration.ofMinutes(10);
    private Duration dependencyPollInterval = Duration.ofSeconds(2);
    private String shareType;
    /** Shared storage capacity, in GB. */
    private int shareSize = 1;
    private String networkCidr = "10.0.0.0/16";
    private String networkDns = "";
    private String networkGateway;
    private int segmentationId = 100;
    private PortBandwidthLimit parentPortBandwidthLimit = PortBandwidthLimit.UNLIMITED;
    private PortBandwidthLimit subPortBandwidthLimit = PortBandwidthLimit.UNLIMITED;
    private String sshUsername = "clouduser";
    private Map<String, String> hostRoutes = new HashMap<>();
}
