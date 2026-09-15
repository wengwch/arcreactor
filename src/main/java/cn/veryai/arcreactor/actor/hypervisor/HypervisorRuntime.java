package cn.veryai.arcreactor.actor.hypervisor;

import cn.veryai.arcreactor.actor.hypervisor.command.HypervisorCommand;
import cn.veryai.arcreactor.actor.hypervisor.projection.ProjectionBootstrap;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

import javax.sql.DataSource;

/** Public runtime facade used by the application bootstrap. */
public final class HypervisorRuntime {

    public static final String ENTITY_TYPE = "Hypervisor";
    public static final EntityTypeKey<HypervisorCommand> TYPE_KEY =
            EntityTypeKey.create(HypervisorCommand.class, ENTITY_TYPE);

    private HypervisorRuntime() {}

    public static void initialize(ActorSystem<?> system, DataSource dataSource,
            SqlSessionFactory sqlSessionFactory) {
        initializeSharding(system);
        if (system.settings().config().getBoolean("cloud.projection.enabled")) {
            ProjectionBootstrap.init(system, dataSource, sqlSessionFactory,
                    system.settings().config().getInt("cloud.projection.slice-ranges"));
        }
    }

    public static void initializeSharding(ActorSystem<?> system) {
        ClusterSharding.get(system).init(Entity.of(TYPE_KEY,
                entityContext -> HypervisorActor.create(entityContext.getEntityId())));
    }
}
