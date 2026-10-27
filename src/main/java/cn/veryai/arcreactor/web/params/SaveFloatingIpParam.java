package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveFloatingIpParam {
    @NotBlank
    @Size(max = 255)
    private String name;
    private String description;
    @NotBlank
    @Size(max = 255)
    private String regionId;
    @NotBlank
    @Size(max = 255)
    private String extNetId;
    @Size(max = 255)
    private String osFloatingIpId;
    @Size(max = 255)
    private String floatingIp;
    @NotNull
    @Size(max = 65535)
    private byte[] portMask = new byte[0];

    private boolean initOpenstack = true;
}
