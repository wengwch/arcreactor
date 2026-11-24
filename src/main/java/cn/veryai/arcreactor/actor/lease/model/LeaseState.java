package cn.veryai.arcreactor.actor.lease.model;

import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import java.io.Serializable;
import java.time.Instant;

public record LeaseState(
    String leaseId,
    String userId,
    String requestId,
    String fingerprint,
    InstanceSpec instanceSpec,
    Phase phase,
    long periodSeq,
    LeasePeriod currentPeriod,
    Instant lastPeriodEndsAt,
    RentalPlan renewalPlan,
    long planRevision,
    boolean autoRenew,
    LeasePurchase pendingRenewal,
    LeaseSettlement pendingSettlement,
    boolean instanceCreationRequested,
    Instant terminatedAt,
    Instant nextActionAt,
    String error) implements Serializable {

  public enum Phase { NEW, WAITING_LOCK, PROVISIONING, ACTIVE, EXPIRED, TERMINATING, TERMINATED }
  public enum PurchasePhase { WAITING_SETTLEMENT, PRICING, CREATING_ORDER, LOCKING, WAITING_ACTIVATION }

  public static LeaseState empty(String leaseId) {
    return new LeaseState(leaseId, null, null, null, null, Phase.NEW, 0, null, null, null, 0, false, null,
        null, false, null, null, null);
  }

  public LeaseState created(String nextUserId, String nextRequestId, String nextFingerprint,
      InstanceSpec nextInstanceSpec, RentalPlan plan, boolean nextAutoRenew) {
    return new LeaseState(leaseId, nextUserId, nextRequestId, nextFingerprint, nextInstanceSpec,
        Phase.WAITING_LOCK, periodSeq, currentPeriod, lastPeriodEndsAt, plan, 1, nextAutoRenew, pendingRenewal,
        pendingSettlement, instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState renewalPlanChanged(RentalPlan plan, long revision) {
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, plan, revision, autoRenew, pendingRenewal, pendingSettlement,
        instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState autoRenewConfigured(boolean enabled) {
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, enabled, pendingRenewal, pendingSettlement,
        instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState purchaseRequested(LeasePurchase purchase) {
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, purchase, pendingSettlement,
        instanceCreationRequested, terminatedAt, nextActionAt, null);
  }

  public LeaseState purchasePriced(String orderId, long amount) {
    if (pendingRenewal == null || !pendingRenewal.orderId().equals(orderId)) {
      return this;
    }
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal.priced(amount),
        pendingSettlement, instanceCreationRequested, terminatedAt, nextActionAt, null);
  }

  public LeaseState purchasePhaseChanged(String orderId, PurchasePhase nextPurchasePhase) {
    if (pendingRenewal == null || !pendingRenewal.orderId().equals(orderId)) {
      return this;
    }
    Phase nextPhase = nextPurchasePhase == PurchasePhase.WAITING_ACTIVATION ? Phase.PROVISIONING : phase;
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, nextPhase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew,
        pendingRenewal.withPhase(nextPurchasePhase), pendingSettlement, instanceCreationRequested,
        terminatedAt, nextActionAt, error);
  }

  public LeaseState settlementRequested(LeaseSettlement settlement) {
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal, settlement,
        instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState settlementCompleted(String orderId) {
    if (pendingSettlement == null || !pendingSettlement.orderId().equals(orderId)) {
      return this;
    }
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal, null,
        instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState periodEnded(Instant endsAt) {
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, Phase.EXPIRED, periodSeq,
        null, endsAt, renewalPlan, planRevision, autoRenew, pendingRenewal, pendingSettlement,
        instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState periodStarted(LeasePeriod period) {
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, Phase.ACTIVE,
        period.periodSeq(), period, period.endsAt(), renewalPlan, planRevision, autoRenew, null,
        pendingSettlement, instanceCreationRequested, terminatedAt, nextActionAt, null);
  }

  public LeaseState terminationRequested(Instant cutoff, String reason) {
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, Phase.TERMINATING, periodSeq,
        null, lastPeriodEndsAt, renewalPlan, planRevision, false, null, pendingSettlement,
        instanceCreationRequested, cutoff, nextActionAt, reason);
  }

  public LeaseState terminated() {
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, Phase.TERMINATED, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal,
        pendingSettlement, instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState withInstanceCreationRequested() {
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal,
        pendingSettlement, true, terminatedAt, nextActionAt, error);
  }

  public LeaseState withError(String nextError) {
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal,
        pendingSettlement, instanceCreationRequested, terminatedAt, nextActionAt, nextError);
  }

  public LeaseState reconciliationScheduled(Instant scheduledAt) {
    return new LeaseState(leaseId, userId, requestId, fingerprint, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal,
        pendingSettlement, instanceCreationRequested, terminatedAt, scheduledAt, error);
  }

  public boolean terminating() {
    return phase == Phase.TERMINATING || phase == Phase.TERMINATED;
  }

  public boolean resourceDeleted() {
    return phase == Phase.TERMINATED;
  }

  public boolean settled() {
    return terminating() && pendingRenewal == null && pendingSettlement == null;
  }
}
