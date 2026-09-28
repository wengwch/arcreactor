package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveImageParam {
    @NotBlank
    @Size(max = 255)
    private String name;
    @Size(max = 255)
    private String distro;
    @Size(max = 255)
    private String version;
    @NotNull
    private Boolean enabled = false;
    @Size(max = 255)
    private String categoryId;
    @NotNull
    private Boolean gpuDriverInstalled = false;
    @Size(max = 255)
    private String gpuDriver;
    @NotBlank
    @Size(max = 255)
    private String osImageId;
    @NotBlank
    @Size(max = 255)
    private String regionId;
}
