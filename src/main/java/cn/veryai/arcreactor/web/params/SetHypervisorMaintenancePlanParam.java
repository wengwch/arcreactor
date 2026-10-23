package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SetHypervisorMaintenancePlanParam {
    @NotNull
    private LocalDateTime expectedMaintenanceTime;
}
