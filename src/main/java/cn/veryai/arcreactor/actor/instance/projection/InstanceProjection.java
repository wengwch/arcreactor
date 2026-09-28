package cn.veryai.arcreactor.actor.instance.projection;

import cn.veryai.arcreactor.actor.instance.InstanceWorkflowEntity;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import javax.sql.DataSource;
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

public final class InstanceProjection {
    public static final String PROJECTION_NAME = "instance-view";

    private InstanceProjection() {}

    public static Projection<EventEnvelope<InstanceWorkflowEvent>> create(ActorSystem<?> system,
            DataSource dataSource, SqlSessionFactory sqlSessionFactory, int minSlice, int maxSlice) {
        SourceProvider<Offset, EventEnvelope<InstanceWorkflowEvent>> source =
                EventSourcedProvider.eventsBySlices(system, R2dbcReadJournal.Identifier(),
                        InstanceWorkflowEntity.ENTITY_TYPE, minSlice, maxSlice);
        return JdbcProjection.exactlyOnce(
                ProjectionId.of(PROJECTION_NAME, minSlice + "-" + maxSlice),
                source,
                () -> new JdbcProjectionSession(dataSource, sqlSessionFactory),
                InstanceProjectionHandler::new,
                system);
    }
}
