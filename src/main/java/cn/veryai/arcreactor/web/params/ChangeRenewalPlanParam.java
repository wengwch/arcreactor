package cn.veryai.arcreactor.web.params;

import cn.veryai.arcreactor.actor.lease.model.RentalPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChangeRenewalPlanParam(@Positive long targetPeriodSeq, @Positive long expectedPlanRevision,
        @NotNull @Valid RentalPlan plan) {}
