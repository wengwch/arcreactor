package cn.veryai.arcreactor.actor.lease.model;

import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record LeaseState(
    String leaseId,
    String userId,
    String requestId,
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

  public enum Phase { NEW, WAITING_LOCK, PROVISIONING, ACTIVE, RENEWING, EXPIRED, TERMINATING, TERMINATED }

  public LeaseState {
    if (pendingRenewal != null && pendingSettlement != null) {
      throw new IllegalArgumentException("Purchase and settlement cannot be pending together");
    }
  }

  public static LeaseState empty(String leaseId) {
    return new LeaseState(leaseId, null, null, null, Phase.NEW, 0, null, null, null, 0, false, null,
        null, false, null, null, null);
  }

  public LeaseState created(String nextUserId, String nextRequestId,
      InstanceSpec nextInstanceSpec, RentalPlan plan, boolean nextAutoRenew) {
    return new LeaseState(leaseId, nextUserId, nextRequestId, nextInstanceSpec,
        Phase.WAITING_LOCK, periodSeq, currentPeriod, lastPeriodEndsAt, plan, 1, nextAutoRenew, pendingRenewal,
        pendingSettlement, instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState renewalPlanChanged(RentalPlan plan, long revision) {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, plan, revision, autoRenew, pendingRenewal, pendingSettlement,
        instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState autoRenewConfigured(boolean enabled) {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, enabled, pendingRenewal, pendingSettlement,
        instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState renewalStarted() {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, Phase.RENEWING, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal, pendingSettlement,
        instanceCreationRequested, terminatedAt, nextActionAt, null);
  }

  public LeaseState purchaseRequested(LeasePurchase purchase) {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, Phase.WAITING_LOCK, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, purchase, pendingSettlement,
        instanceCreationRequested, terminatedAt, nextActionAt, null);
  }

  public LeaseState purchasePriced(BigDecimal amount) {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew,
        pendingRenewal.priced(amount), pendingSettlement, instanceCreationRequested,
        terminatedAt, nextActionAt, null);
  }

  public LeaseState purchaseLocked() {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, Phase.PROVISIONING, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal, pendingSettlement,
        instanceCreationRequested, terminatedAt, nextActionAt, null);
  }

  public LeaseState settlementRequested(LeaseSettlement settlement) {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal, settlement,
        instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState settlementCompleted() {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal, null,
        instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState periodEnded(Instant endsAt) {
    Phase nextPhase = phase == Phase.RENEWING ? Phase.RENEWING : Phase.EXPIRED;
    return new LeaseState(leaseId, userId, requestId, instanceSpec, nextPhase, periodSeq,
        null, endsAt, renewalPlan, planRevision, autoRenew, pendingRenewal, pendingSettlement,
        instanceCreationRequested, terminatedAt, nextActionAt, null);
  }

  public LeaseState periodStarted(LeasePeriod period) {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, Phase.ACTIVE,
        period.periodSeq(), period, period.endsAt(), renewalPlan, planRevision, autoRenew, null,
        pendingSettlement, instanceCreationRequested, terminatedAt, nextActionAt, null);
  }

  public LeaseState terminationStarted(Instant terminationTime) {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, Phase.TERMINATING, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, false, pendingRenewal, pendingSettlement,
        instanceCreationRequested, terminationTime, nextActionAt, null);
  }

  /** Clearing the current period prevents settlement from being generated again after recovery. */
  public LeaseState terminationPrepared(Instant terminationTime, String reason) {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, Phase.TERMINATING, periodSeq,
        null, lastPeriodEndsAt, renewalPlan, planRevision, false, null, pendingSettlement,
        instanceCreationRequested, terminationTime, nextActionAt, reason);
  }

  public LeaseState terminated() {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, Phase.TERMINATED, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal,
        pendingSettlement, instanceCreationRequested, terminatedAt, nextActionAt, error);
  }

  public LeaseState withInstanceCreationRequested() {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal,
        pendingSettlement, true, terminatedAt, nextActionAt, error);
  }

  public LeaseState withError(String nextError) {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal,
        pendingSettlement, instanceCreationRequested, terminatedAt, nextActionAt, nextError);
  }

  public LeaseState reconciliationScheduled(Instant scheduledAt) {
    return new LeaseState(leaseId, userId, requestId, instanceSpec, phase, periodSeq,
        currentPeriod, lastPeriodEndsAt, renewalPlan, planRevision, autoRenew, pendingRenewal,
        pendingSettlement, instanceCreationRequested, terminatedAt, scheduledAt, error);
  }

  public boolean terminating() {
    return phase == Phase.TERMINATING || phase == Phase.TERMINATED;
  }

  public boolean periodDue(Instant now) {
    return !terminating() && currentPeriod != null && !now.isBefore(currentPeriod.endsAt());
  }

  /** Includes accepted renewal before the next purchase exists. */
  public boolean hasPendingRenewal() {
    return phase == Phase.RENEWING || pendingRenewal != null;
  }

  public boolean hasPendingWork() {
    return hasPendingRenewal() || pendingSettlement != null || phase == Phase.TERMINATING;
  }

  public boolean settled() {
    return terminating() && currentPeriod == null && pendingRenewal == null && pendingSettlement == null;
  }
}
