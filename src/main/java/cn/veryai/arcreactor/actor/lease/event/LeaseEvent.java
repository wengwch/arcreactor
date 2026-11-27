package cn.veryai.arcreactor.actor.lease.event;

import java.io.Serializable;

public sealed interface LeaseEvent extends Serializable permits LeaseCreated, RenewalPlanChanged,
    AutoRenewConfigured, RenewalStarted, PurchaseRequested, PurchasePriced, PurchaseLocked,
    SettlementRequested, SettlementCompleted, PeriodEnded, PeriodStarted,
    LeaseTerminationStarted, LeaseTerminationPrepared, LeaseTerminated, InstanceCreationRequested,
    LeaseRetryScheduled, LeaseReconciliationScheduled {}
