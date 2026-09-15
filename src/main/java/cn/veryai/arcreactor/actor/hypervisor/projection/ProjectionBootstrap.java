package cn.veryai.arcreactor.actor.hypervisor.projection;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.persistence.r2dbc.query.javadsl.R2dbcReadJournal;
import org.apache.pekko.projection.ProjectionBehavior;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;

import javax.sql.DataSource;
import java.util.List;

public final class ProjectionBootstrap {
    private ProjectionBootstrap() {}

    public static void init(ActorSystem<?> system, DataSource dataSource,
            SqlSessionFactory sqlSessionFactory, int numberOfSliceRanges) {
        List<Pair<Integer, Integer>> ranges = EventSourcedProvider.sliceRanges(
                system, R2dbcReadJournal.Identifier(), numberOfSliceRanges);
        ShardedDaemonProcess.get(system).init(
                ProjectionBehavior.Command.class,
                "hypervisor-resource-projection",
                ranges.size(),
                index -> {
                    Pair<Integer, Integer> range = ranges.get(index);
                    return ProjectionBehavior.create(HypervisorProjection.create(
                            system, dataSource, sqlSessionFactory, range.first(), range.second()));
                });
    }
}
