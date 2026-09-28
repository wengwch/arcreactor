package cn.veryai.arcreactor.actor;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorActor;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.command.HypervisorCommand;
import cn.veryai.arcreactor.actor.hypervisor.internal.DefaultHypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.projection.HypervisorProjection;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.SchedulerFactory;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.SchedulerService;
import cn.veryai.arcreactor.actor.instance.InstanceWorkflowActor;
import cn.veryai.arcreactor.actor.instance.InstanceWorkflowSettings;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.repo.HypervisorReadRepo;
import com.typesafe.config.Config;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public final class PekkoRuntime {
  @Getter @Autowired private ActorSystem<Void> actorSystem;
  private final ClusterSharding sharding;

  @Autowired private OpenStackClient openStackClient;

  @Autowired private HypervisorPlacement hypervisorPlacement;

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

  public PekkoRuntime(
      DataSource dataSource,
      SqlSessionFactory sqlSessionFactory,
      HypervisorPlacement hypervisorPlacement)
      throws Exception {
    Config config = actorSystem.settings().config().getConfig("cloud.instance");
    InstanceWorkflowSettings settings =
        new InstanceWorkflowSettings(
            duration(config, "cloud.reservation.ttl"),
            duration(config, "provisioning-timeout"),
            duration(config, "poll.initial-delay"),
            duration(config, "poll.max-delay"));

    try {
      sharding = ClusterSharding.get(actorSystem);
      sharding.init(
          Entity.of(
              HypervisorActor.TYPE_KEY, context -> HypervisorActor.create(context.getEntityId())));
      sharding.init(
          Entity.of(
              InstanceWorkflowActor.TYPE_KEY,
              context ->
                  InstanceWorkflowActor.create(
                      context.getEntityId(), hypervisorPlacement, openStackClient, settings)));
      if (actorSystem.settings().config().getBoolean("cloud.projection.enabled")) {
        HypervisorProjection.init(
            actorSystem,
            dataSource,
            sqlSessionFactory,
            actorSystem.settings().config().getInt("cloud.projection.slice-ranges"));
      }
    } catch (Exception ex) {
      actorSystem.terminate();
      try {
        actorSystem.getWhenTerminated().toCompletableFuture().get(45, TimeUnit.SECONDS);
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
    actorSystem.terminate();
    actorSystem.getWhenTerminated().toCompletableFuture().get(45, TimeUnit.SECONDS);
  }

  private static Duration duration(Config config, String path) {
    return Duration.ofMillis(config.getDuration(path).toMillis());
  }
}
