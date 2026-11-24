package cn.veryai.arcreactor.vo;

import cn.veryai.arcreactor.actor.lease.model.LeasePeriod;
import cn.veryai.arcreactor.actor.lease.model.LeasePurchase;
import cn.veryai.arcreactor.actor.lease.model.LeaseSettlement;
import cn.veryai.arcreactor.actor.lease.model.LeaseState;
import cn.veryai.arcreactor.actor.lease.model.RentalPlan;
import cn.veryai.arcreactor.entity.LeaseViewEntity;
import java.time.Instant;

/** Public rental status excludes the persisted instance credentials. */
public record LeaseView(String leaseId, String userId, LeaseState.Phase phase, long periodSeq,
        LeasePeriod currentPeriod, Instant lastPeriodEndsAt, RentalPlan renewalPlan, long planRevision, boolean autoRenew,
        LeasePurchase pendingRenewal, LeaseSettlement pendingSettlement,
        Instant terminatedAt, Instant nextActionAt, boolean settled, String error) {

    public static LeaseView from(LeaseState state) {
        return new LeaseView(state.leaseId(), state.userId(), state.phase(), state.periodSeq(), state.currentPeriod(),
                state.lastPeriodEndsAt(), state.renewalPlan(), state.planRevision(), state.autoRenew(),
                state.pendingRenewal(), state.pendingSettlement(), state.terminatedAt(), state.nextActionAt(),
                state.settled(), state.error());
    }

    public static LeaseView from(LeaseViewEntity view) {
        return new LeaseView(view.getLeaseId(), view.getUserId(), view.getPhase(), view.getPeriodSeq(),
                view.getCurrentPeriod(), view.getLastPeriodEndsAt(), view.getRenewalPlan(), view.getPlanRevision(),
                view.isAutoRenew(), view.getPendingRenewal(), view.getPendingSettlement(), view.getTerminatedAt(),
                view.getNextActionAt(), view.settled(), view.getError());
    }

    public record Created(LeaseView lease, String username, String password, String jupyterToken) {}
    public record Mutation(boolean accepted, String error, LeaseView lease) {}
}
