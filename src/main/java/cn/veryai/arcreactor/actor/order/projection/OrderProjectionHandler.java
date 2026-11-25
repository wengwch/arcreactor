package cn.veryai.arcreactor.actor.order.projection;

import cn.veryai.arcreactor.actor.order.OrderActor;
import cn.veryai.arcreactor.actor.order.event.OrderCreated;
import cn.veryai.arcreactor.actor.order.event.OrderDeducted;
import cn.veryai.arcreactor.actor.order.event.OrderDeductionRequested;
import cn.veryai.arcreactor.actor.order.event.OrderEvent;
import cn.veryai.arcreactor.actor.order.event.OrderInsufficientFunds;
import cn.veryai.arcreactor.actor.order.event.OrderLocked;
import cn.veryai.arcreactor.actor.order.event.OrderOperationFailed;
import cn.veryai.arcreactor.actor.order.event.UnopenedOrderSettled;
import cn.veryai.arcreactor.actor.order.projection.mybatis.OrderProjectionMapper;
import cn.veryai.arcreactor.actor.projection.RentalProjectionSession;
import java.sql.Timestamp;
import java.time.Instant;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

public final class OrderProjectionHandler extends JdbcHandler<EventEnvelope<OrderEvent>, RentalProjectionSession> {
    @Override
    public void process(RentalProjectionSession session, EventEnvelope<OrderEvent> envelope) {
        String orderId = entityId(envelope.persistenceId());
        OrderProjectionMapper mapper = session.mapper(OrderProjectionMapper.class);
        Long projectedVersion = mapper.findVersionForUpdate(orderId);
        long version = envelope.sequenceNr();
        long previousVersion = projectedVersion == null ? 0 : projectedVersion;
        if (version <= previousVersion) {
            return;
        }
        if (version != previousVersion + 1) {
            throw new IllegalStateException("Missing order projection events for " + orderId
                    + ": projected " + previousVersion + ", received " + version);
        }
        Timestamp updatedAt = Timestamp.from(Instant.ofEpochMilli(envelope.timestamp()));
        int written = switch (envelope.event()) {
            case OrderCreated created -> mapper.insertCreated(orderId, created.userId(), created.leaseId(),
                    created.intentId(), created.amount(), version, updatedAt);
            case UnopenedOrderSettled settled -> mapper.insertUnopenedSettled(orderId, settled.userId(), settled.leaseId(),
                    settled.intentId(), version, updatedAt);
            case OrderLocked ignored -> mapper.updateLocked(orderId, version, updatedAt);
            case OrderDeductionRequested requested ->
                    mapper.updateDeductionRequested(orderId, requested.amount(), version, updatedAt);
            case OrderDeducted ignored -> mapper.updateDeducted(orderId, version, updatedAt);
            case OrderInsufficientFunds ignored -> mapper.updateInsufficientFunds(orderId, version, updatedAt);
            case OrderOperationFailed failed -> mapper.updateError(orderId, failed.error(), version, updatedAt);
        };
        if (written != 1) {
            throw new IllegalStateException("Order projection changed while applying " + orderId + " event " + version);
        }
    }

    static String entityId(String persistenceId) {
        String prefix = OrderActor.ENTITY_TYPE + "|";
        if (persistenceId == null || !persistenceId.startsWith(prefix)
                || persistenceId.substring(prefix.length()).isBlank()) {
            throw new IllegalArgumentException("Unexpected order persistence id: " + persistenceId);
        }
        return persistenceId.substring(prefix.length());
    }
}
