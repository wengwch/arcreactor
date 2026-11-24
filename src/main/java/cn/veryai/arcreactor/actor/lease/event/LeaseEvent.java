package cn.veryai.arcreactor.actor.lease.event;

import java.io.Serializable;

public sealed interface LeaseEvent extends Serializable permits LeaseCreated, RenewalPlanChanged,
    AutoRenewConfigured, PurchaseRequested, PurchasePriced, PurchasePhaseChanged,
    SettlementRequested, SettlementCompleted, PeriodEnded, PeriodStarted,
    LeaseTerminationRequested, LeaseTerminated, InstanceCreationRequested,
    LeaseRetryScheduled, LeaseReconciliationScheduled {}
