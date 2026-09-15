package cn.veryai.arcreactor.actor.hypervisor.projection;

import cn.veryai.arcreactor.actor.hypervisor.event.HypervisorEvent;
import cn.veryai.arcreactor.mapper.HypervisorProjectionMapper;
import cn.veryai.arcreactor.actor.hypervisor.projection.repository.HypervisorReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public final class HypervisorProjectionHandler
        extends JdbcHandler<EventEnvelope<HypervisorEvent>, JdbcProjectionSession> {
    private final HypervisorReadRepository repository;

    @Override
    public void process(JdbcProjectionSession session, EventEnvelope<HypervisorEvent> envelope) throws Exception {
        String hypervisorId = entityId(envelope.persistenceId());
        Instant timestamp = Instant.ofEpochMilli(envelope.timestamp());
        repository.apply(session.mapper(HypervisorProjectionMapper.class), hypervisorId,
                envelope.event(), envelope.sequenceNr(), timestamp);
        log.info("projection processed hypervisorId={} event={} resourceVersion={}", hypervisorId,
                envelope.event().getClass().getSimpleName(), envelope.sequenceNr());
    }

    static String entityId(String persistenceId) {
        int separator = persistenceId.indexOf('|');
        if (separator < 0 || separator == persistenceId.length() - 1) {
            throw new IllegalArgumentException("unexpected persistence id: " + persistenceId);
        }
        return persistenceId.substring(separator + 1);
    }
}
