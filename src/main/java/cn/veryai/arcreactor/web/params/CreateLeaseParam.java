package cn.veryai.arcreactor.web.params;

import cn.veryai.arcreactor.actor.lease.model.RentalPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLeaseParam(@NotBlank @Size(max = 128) String requestId,
        @NotNull @Valid CreateInstanceParam instance,
        @NotNull @Valid RentalPlan plan, boolean autoRenew) {}
