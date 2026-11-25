package cn.veryai.arcreactor.entity;

import cn.veryai.arcreactor.actor.lease.model.LeaseState.Phase;
import cn.veryai.arcreactor.actor.lease.model.LeaseState.PurchasePhase;
import cn.veryai.arcreactor.actor.lease.model.RentalPlan.Unit;
import java.math.BigDecimal;
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
    private BigDecimal currentAmount;
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
    private BigDecimal pendingAmount;
    private Long pendingPlanRevision;
    private PurchasePhase pendingPhase;
    private String settlementOrderId;
    private String settlementIntentId;
    private BigDecimal settlementAmount;
    private BigDecimal settlementDeductAmount;
    private Instant terminatedAt;
    private Instant nextActionAt;
    private String error;
}
