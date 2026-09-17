package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import cn.veryai.arcreactor.enums.SysNetworkType;
import org.openstack4j.model.network.NetworkType;

import java.util.HashMap;
import java.util.Map;

@Data
public class SaveSysNetworkParam {
    @NotBlank
    @Size(max = 255)
    private String name;
    private String description;
    @Size(max = 255)
    private String ipv4CIDR;
    @Size(max = 255)
    private String gateway;
    @Size(max = 255)
    private String dns;
    @Size(max = 255)
    private String segmentId;
    private SysNetworkType sysNetworkType;
    @Size(max = 255)
    private String physicalNetwork;
    @NotNull
    private Boolean routerExternally = false;
    @NotNull
    private Boolean shared = false;
    @Size(max = 255)
    private String osNetId;
    @Size(max = 255)
    private String osSubNetId;
    private NetworkType osNetworkType;
    @Size(max = 255)
    private String osProjectId;
    @NotBlank
    @Size(max = 255)
    private String regionId;
    @NotNull
    private Map<@NotBlank String, @NotBlank String> hostRoute = new HashMap<>();

    private boolean initOpenstack = true;
}
