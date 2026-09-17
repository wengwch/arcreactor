package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveFlavorParam {
    @NotBlank
    @Size(max = 255)
    private String name;
    private String description;
    @PositiveOrZero
    private int vcpus;
    @PositiveOrZero
    private int ram;
    @PositiveOrZero
    private int disk;
    @PositiveOrZero
    private int gpus;
    @Size(max = 255)
    private String hypervisorType;
    @Size(max = 255)
    private String cpuType;
    @Size(max = 255)
    private String ramType;
    @Size(max = 255)
    private String diskType;
    @Size(max = 255)
    private String gpuType;
    @NotBlank
    @Size(max = 255)
    private String regionId;
    @NotNull
    private Boolean enabled = true;
    @NotBlank
    @Size(max = 255)
    private String osFlavorId;
}
