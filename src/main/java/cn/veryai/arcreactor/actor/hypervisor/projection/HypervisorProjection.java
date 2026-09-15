package cn.veryai.arcreactor.actor.hypervisor.projection;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorRuntime;
import cn.veryai.arcreactor.actor.hypervisor.event.HypervisorEvent;
import cn.veryai.arcreactor.actor.hypervisor.projection.repository.JdbcHypervisorReadRepository;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.persistence.r2dbc.query.javadsl.R2dbcReadJournal;
import org.apache.pekko.projection.Projection;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.projection.jdbc.javadsl.JdbcProjection;

import javax.sql.DataSource;

public final class HypervisorProjection {
    public static final String PROJECTION_NAME = "hypervisor-resource";

    private HypervisorProjection() {}

    public static Projection<EventEnvelope<HypervisorEvent>> create(ActorSystem<?> system,
            DataSource dataSource, SqlSessionFactory sqlSessionFactory, int minSlice, int maxSlice) {
        SourceProvider<Offset, EventEnvelope<HypervisorEvent>> source = EventSourcedProvider.eventsBySlices(
                system, R2dbcReadJournal.Identifier(), HypervisorRuntime.ENTITY_TYPE, minSlice, maxSlice);
        return JdbcProjection.exactlyOnce(
                ProjectionId.of(PROJECTION_NAME, minSlice + "-" + maxSlice),
                source,
                () -> new JdbcProjectionSession(dataSource, sqlSessionFactory),
                () -> new HypervisorProjectionHandler(new JdbcHypervisorReadRepository()),
                system);
    }
}
