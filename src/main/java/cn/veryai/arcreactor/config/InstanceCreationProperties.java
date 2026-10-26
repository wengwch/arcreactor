package cn.veryai.arcreactor.config;

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
    private String shareType;
    /** Shared storage capacity, in GB. */
    private int shareSize = 100;
    private String networkCidr = "10.0.0.0/16";
    private String networkDns = "";
    private String networkGateway;
    private int segmentationId = 100;
    private String sshUsername = "clouduser";
    private Map<String, String> hostRoutes = new HashMap<>();
}
