package cn.veryai.arcreactor.actor.lease.projection;

import cn.veryai.arcreactor.actor.lease.LeaseActor;
import cn.veryai.arcreactor.actor.lease.event.AutoRenewConfigured;
import cn.veryai.arcreactor.actor.lease.event.InstanceCreationRequested;
import cn.veryai.arcreactor.actor.lease.event.LeaseCreated;
import cn.veryai.arcreactor.actor.lease.event.LeaseEvent;
import cn.veryai.arcreactor.actor.lease.event.LeaseReconciliationScheduled;
import cn.veryai.arcreactor.actor.lease.event.LeaseRetryScheduled;
import cn.veryai.arcreactor.actor.lease.event.LeaseTerminated;
import cn.veryai.arcreactor.actor.lease.event.LeaseTerminationRequested;
import cn.veryai.arcreactor.actor.lease.event.PeriodEnded;
import cn.veryai.arcreactor.actor.lease.event.PeriodStarted;
import cn.veryai.arcreactor.actor.lease.event.PurchasePhaseChanged;
import cn.veryai.arcreactor.actor.lease.event.PurchasePriced;
import cn.veryai.arcreactor.actor.lease.event.PurchaseRequested;
import cn.veryai.arcreactor.actor.lease.event.RenewalPlanChanged;
import cn.veryai.arcreactor.actor.lease.event.SettlementCompleted;
import cn.veryai.arcreactor.actor.lease.event.SettlementRequested;
import cn.veryai.arcreactor.actor.lease.projection.mybatis.LeaseProjectionMapper;
import cn.veryai.arcreactor.actor.projection.RentalProjectionSession;
import java.sql.Timestamp;
import java.time.Instant;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

public final class LeaseProjectionHandler extends JdbcHandler<EventEnvelope<LeaseEvent>, RentalProjectionSession> {
    @Override
    public void process(RentalProjectionSession session, EventEnvelope<LeaseEvent> envelope) {
        String leaseId = entityId(envelope.persistenceId());
        LeaseProjectionMapper mapper = session.mapper(LeaseProjectionMapper.class);
        Long projectedVersion = mapper.findVersionForUpdate(leaseId);
        long version = envelope.sequenceNr();
        long previousVersion = projectedVersion == null ? 0 : projectedVersion;
        if (version <= previousVersion) {
            return;
        }
        if (version != previousVersion + 1) {
            throw new IllegalStateException("Missing lease projection events for " + leaseId
                    + ": projected " + previousVersion + ", received " + version);
        }
        Timestamp updatedAt = Timestamp.from(Instant.ofEpochMilli(envelope.timestamp()));
        int written = switch (envelope.event()) {
            case LeaseCreated created -> mapper.insertCreated(leaseId, created.userId(), created.plan(),
                    created.autoRenew(), version, updatedAt);
            case RenewalPlanChanged changed -> mapper.updateRenewalPlan(leaseId, changed.plan(),
                    changed.planRevision(), version, updatedAt);
            case AutoRenewConfigured configured ->
                    mapper.updateAutoRenew(leaseId, configured.enabled(), version, updatedAt);
            case PurchaseRequested requested ->
                    mapper.updatePurchaseRequested(leaseId, requested.purchase(), version, updatedAt);
            case PurchasePriced priced ->
                    mapper.updatePurchasePriced(leaseId, priced.orderId(), priced.amount(), version, updatedAt);
            case PurchasePhaseChanged changed ->
                    mapper.updatePurchasePhase(leaseId, changed.orderId(), changed.phase(), version, updatedAt);
            case SettlementRequested requested ->
                    mapper.updateSettlementRequested(leaseId, requested.settlement(), version, updatedAt);
            case SettlementCompleted completed ->
                    mapper.updateSettlementCompleted(leaseId, completed.orderId(), version, updatedAt);
            case PeriodEnded ended -> mapper.updatePeriodEnded(leaseId, ended.endsAt(), version, updatedAt);
            case PeriodStarted started -> mapper.updatePeriodStarted(leaseId, started.period(), version, updatedAt);
            case LeaseTerminationRequested requested -> mapper.updateTermination(leaseId, requested.terminatedAt(),
                    requested.reason(), version, updatedAt);
            case LeaseTerminated ignored -> mapper.updateTerminated(leaseId, version, updatedAt);
            case InstanceCreationRequested ignored -> mapper.updateVersion(leaseId, version, updatedAt);
            case LeaseRetryScheduled retry -> mapper.updateError(leaseId, retry.error(), version, updatedAt);
            case LeaseReconciliationScheduled scheduled ->
                    mapper.updateNextAction(leaseId, scheduled.nextActionAt(), version, updatedAt);
        };
        if (written != 1) {
            throw new IllegalStateException("Lease projection changed while applying " + leaseId + " event " + version);
        }
    }

    static String entityId(String persistenceId) {
        String prefix = LeaseActor.ENTITY_TYPE + "|";
        if (persistenceId == null || !persistenceId.startsWith(prefix)
                || persistenceId.substring(prefix.length()).isBlank()) {
            throw new IllegalArgumentException("Unexpected lease persistence id: " + persistenceId);
        }
        return persistenceId.substring(prefix.length());
    }
}
