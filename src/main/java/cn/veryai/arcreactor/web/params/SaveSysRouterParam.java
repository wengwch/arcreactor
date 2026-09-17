package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveSysRouterParam {
    @NotBlank
    @Size(max = 255)
    private String name;
    @Size(max = 255)
    private String extNetId;
    @Size(max = 255)
    private String osRouterId;
    @Size(max = 255)
    private String osProjectId;
    @Size(max = 255)
    private String osExtNetId;
    @Size(max = 255)
    private String osSharedNetSubnetId;
    @Size(max = 255)
    private String osSharedNetPortId;
    @NotBlank
    @Size(max = 255)
    private String regionId;
}
