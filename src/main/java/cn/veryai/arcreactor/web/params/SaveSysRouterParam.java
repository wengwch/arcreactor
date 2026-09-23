package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveSysRouterParam {
    @NotBlank
    @Size(max = 255)
    private String name;
    @NotBlank
    @Size(max = 255)
    private String extNetId;
    @NotBlank
    @Size(max = 255)
    private String sharedNetId;

    @Size(max = 255)
    private String osRouterId;
    @Size(max = 255)
    private String osExtNetId;
    @Size(max = 255)
    private String osSharedNetId;
    @NotBlank
    @Size(max = 255)
    private String regionId;

    private boolean initOpenstack = true;
}
