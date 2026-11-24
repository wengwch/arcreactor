package cn.veryai.arcreactor.entity;

import cn.veryai.arcreactor.actor.lease.model.LeasePeriod;
import cn.veryai.arcreactor.actor.lease.model.LeasePurchase;
import cn.veryai.arcreactor.actor.lease.model.LeaseSettlement;
import cn.veryai.arcreactor.actor.lease.model.LeaseState.Phase;
import cn.veryai.arcreactor.actor.lease.model.LeaseState.PurchasePhase;
import cn.veryai.arcreactor.actor.lease.model.RentalPlan;
import cn.veryai.arcreactor.actor.lease.model.RentalPlan.Unit;
import java.time.Instant;
import lombok.Data;

/** Query data maintained by concrete lease events; instance credentials stay in the journal. */
@Data
public class LeaseViewEntity {
    private String leaseId;
    private String userId;
    private Phase phase;
    private long periodSeq;
    private String currentOrderId;
    private String currentIntentId;
    private Unit currentPlanUnit;
    private Integer currentPlanQuantity;
    private Long currentAmount;
    private Instant currentStartsAt;
    private Instant currentEndsAt;
    private Instant lastPeriodEndsAt;
    private Unit renewalPlanUnit;
    private Integer renewalPlanQuantity;
    private long planRevision;
    private boolean autoRenew;
    private String pendingOrderId;
    private String pendingIntentId;
    private Long pendingPeriodSeq;
    private Unit pendingPlanUnit;
    private Integer pendingPlanQuantity;
    private Long pendingAmount;
    private Long pendingPlanRevision;
    private PurchasePhase pendingPhase;
    private String settlementOrderId;
    private String settlementIntentId;
    private Long settlementAmount;
    private Long settlementDeductAmount;
    private Instant terminatedAt;
    private Instant nextActionAt;
    private String error;
    private long workflowVersion;
    private Instant updatedAt;

    public LeasePeriod getCurrentPeriod() {
        return currentOrderId == null ? null : new LeasePeriod(periodSeq, currentOrderId, currentIntentId,
                new RentalPlan(currentPlanUnit, currentPlanQuantity), currentAmount, currentStartsAt, currentEndsAt);
    }

    public RentalPlan getRenewalPlan() {
        return renewalPlanUnit == null ? null : new RentalPlan(renewalPlanUnit, renewalPlanQuantity);
    }

    public LeasePurchase getPendingRenewal() {
        return pendingOrderId == null ? null : new LeasePurchase(pendingOrderId, pendingIntentId, pendingPeriodSeq,
                new RentalPlan(pendingPlanUnit, pendingPlanQuantity), pendingAmount, pendingPlanRevision, pendingPhase);
    }

    public LeaseSettlement getPendingSettlement() {
        return settlementOrderId == null ? null : new LeaseSettlement(
                settlementOrderId, settlementIntentId, settlementAmount, settlementDeductAmount);
    }

    public boolean settled() {
        return (phase == Phase.TERMINATING || phase == Phase.TERMINATED)
                && pendingOrderId == null && settlementOrderId == null;
    }
}
