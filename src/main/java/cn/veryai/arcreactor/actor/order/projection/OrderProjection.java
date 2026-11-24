package cn.veryai.arcreactor.actor.order.projection;

import cn.veryai.arcreactor.actor.order.OrderActor;
import cn.veryai.arcreactor.actor.order.event.OrderEvent;
import cn.veryai.arcreactor.actor.projection.EventLoadingSourceProvider;
import cn.veryai.arcreactor.actor.projection.RentalProjectionSession;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.persistence.r2dbc.query.javadsl.R2dbcReadJournal;
import org.apache.pekko.projection.Projection;
import org.apache.pekko.projection.ProjectionBehavior;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.projection.jdbc.javadsl.JdbcProjection;

public final class OrderProjection {
    // A new offset namespace rebuilds the column-based tables from the retained event journal.
    public static final String PROJECTION_NAME = OrderActor.ENTITY_TYPE + "-rental-columns-v1";

    private OrderProjection() {}

    public static Projection<EventEnvelope<OrderEvent>> create(ActorSystem<?> system,
            DataSource dataSource, SqlSessionFactory sqlSessionFactory, int minSlice, int maxSlice) {
        SourceProvider<Offset, EventEnvelope<OrderEvent>> source = EventSourcedProvider.eventsBySlices(
                system, R2dbcReadJournal.Identifier(), OrderActor.ENTITY_TYPE, minSlice, maxSlice);
        return JdbcProjection.exactlyOnce(
                ProjectionId.of(PROJECTION_NAME, minSlice + "-" + maxSlice),
                new EventLoadingSourceProvider<>(source),
                () -> new RentalProjectionSession(dataSource, sqlSessionFactory),
                OrderProjectionHandler::new,
                system);
    }

    public static void init(ActorSystem<?> system, DataSource dataSource,
            SqlSessionFactory sqlSessionFactory, int numberOfSliceRanges) {
        var ranges = EventSourcedProvider.sliceRanges(
                system, R2dbcReadJournal.Identifier(), numberOfSliceRanges);
        ShardedDaemonProcess.get(system).init(
                ProjectionBehavior.Command.class,
                PROJECTION_NAME + "-projection",
                ranges.size(),
                index -> {
                    var range = ranges.get(index);
                    return ProjectionBehavior.create(create(system, dataSource, sqlSessionFactory, range.first(), range.second()));
                });
    }
}
