package cn.veryai.arcreactor.actor;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorActor;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;

public final class RootBehavior {

    public static Behavior<Void> create() {

        return Behaviors.setup(context -> {

            ActorSystem<Void> system =
                context.getSystem();

            ClusterSharding sharding =
                ClusterSharding.get(system);

            sharding.init(
                Entity.of(
                    HypervisorEntity.TYPE_KEY,
                    entityContext ->
                        HypervisorActor.create(
                            entityContext.getEntityId()
                        )
                )
            );

            return Behaviors.empty();
        });
    }
}