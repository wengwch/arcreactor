package cn.veryai.arcreactor.actor;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorActor;
import cn.veryai.arcreactor.actor.hypervisor.command.DrainHypervisor;
import cn.veryai.arcreactor.actor.hypervisor.command.EnableHypervisor;
import cn.veryai.arcreactor.actor.hypervisor.command.HypervisorCommand;
import cn.veryai.arcreactor.actor.hypervisor.projection.HypervisorProjection;
import cn.veryai.arcreactor.actor.hypervisor.reply.ActionReply;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.TimeUnit;


@Component
public final class PekkoRuntime {
    @Getter
    private final ActorSystem<Void> system;
    private final ClusterSharding sharding;

//    @PostConstruct
//    public void init() {
//        AskPattern.<HypervisorCommand, ActionReply>ask(
//                        hypervisorActor("hv-01"),
//                        replyTo -> new DrainHypervisor(replyTo),
//                        Duration.ofSeconds(5),
//                        system.scheduler())
//                .handle((reply, error) -> {
//                    return reply;
//                });
//    }

    public PekkoRuntime(DataSource dataSource, SqlSessionFactory sqlSessionFactory, Environment environment) throws Exception {
        system = ActorSystem.create(Behaviors.empty(), "cloud-resource-manager");

        try {
            sharding = ClusterSharding.get(system);
            sharding.init(Entity.of(HypervisorActor.TYPE_KEY,
                    context -> HypervisorActor.create(context.getEntityId())));
            if (system.settings().config().getBoolean("cloud.projection.enabled")) {
                HypervisorProjection.init(system, dataSource, sqlSessionFactory, system.settings().config().getInt("cloud.projection.slice-ranges"));
            }
        } catch (Exception ex) {
            system.terminate();
            try {
                system.getWhenTerminated().toCompletableFuture().get(45, TimeUnit.SECONDS);
            } catch (Exception shutdownError) {
                ex.addSuppressed(shutdownError);
            }
            throw ex;
        }
    }

    public EntityRef<HypervisorCommand> hypervisorActor(String id) {
        return sharding.entityRefFor(HypervisorActor.TYPE_KEY, id);
    }

    @PreDestroy
    public void close() throws Exception {
        // Injected dependencies outlive this bean; stop projections before Spring closes the pool.
        system.terminate();
        system.getWhenTerminated().toCompletableFuture().get(45, TimeUnit.SECONDS);
    }
}
