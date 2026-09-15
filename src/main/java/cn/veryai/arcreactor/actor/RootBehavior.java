package cn.veryai.arcreactor.actor;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorActor;
import cn.veryai.arcreactor.actor.hypervisor.projection.ProjectionBootstrap;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class RootBehavior {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;


    public Behavior<Void> create() {

        return Behaviors.setup(context -> {

            ActorSystem<Void> system =
                    context.getSystem();

            ClusterSharding sharding =
                    ClusterSharding.get(system);

            sharding.init(
                    Entity.of(
                            HypervisorActor.TYPE_KEY,
                            entityContext ->
                                    HypervisorActor.create(
                                            entityContext.getEntityId()
                                    )
                    )
            );

            if (system.settings().config().getBoolean("cloud.projection.enabled")) {
                ProjectionBootstrap.init(system, dataSource, sqlSessionFactory,
                        system.settings().config().getInt("cloud.projection.slice-ranges"));
            }

            return Behaviors.empty();
        });
    }
}