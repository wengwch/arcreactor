package cn.veryai.arcreactor.web.params;

import cn.veryai.arcreactor.enums.HypervisorStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

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
    private int vcpus;
    @PositiveOrZero
    private int ram;
    @PositiveOrZero
    private int disk;
    @PositiveOrZero
    private int gpus;
    @PositiveOrZero
    private int usedVcpus;
    @PositiveOrZero
    private int usedRam;
    @PositiveOrZero
    private int usedDisk;
    @PositiveOrZero
    private int usedGpus;
    @NotNull
    private HypervisorStatus hypervisorStatus = HypervisorStatus.MAINTENANCE;
    private LocalDateTime expectedMaintenanceTime;
    @NotNull
    private Boolean enabled = true;
    @NotBlank
    @Size(max = 255)
    private String regionId;
}
