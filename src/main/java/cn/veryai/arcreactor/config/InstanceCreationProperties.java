package cn.veryai.arcreactor.config;

import cn.veryai.arcreactor.actor.instance.model.PortBandwidthLimit;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
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
    /** Each internal service receives its own external TCP/UDP port. */
    private List<Integer> forwardingPorts = List.of(22, 8888);
    private String shareType;
    /** Shared storage capacity, in GB. */
    private int shareSize = 1;
    private String networkCidr = "10.0.0.0/16";
    private String networkDns = "";
    private String networkGateway;
    private int segmentationId = 100;
    private PortBandwidthLimit parentPortBandwidthLimit = new PortBandwidthLimit(1024*100, 1024*10);
    private PortBandwidthLimit subPortBandwidthLimit = new PortBandwidthLimit(1024*100, 1024*10);
    private String sshUsername = "ubuntu";
    private Map<String, String> hostRoutes = new HashMap<>();
}
