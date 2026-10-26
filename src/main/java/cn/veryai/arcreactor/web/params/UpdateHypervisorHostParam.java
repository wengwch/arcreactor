package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateHypervisorHostParam {
    @NotBlank
    @Size(max = 255)
    private String name;
    private String description;
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
    @PositiveOrZero
    private int gpus;
}
