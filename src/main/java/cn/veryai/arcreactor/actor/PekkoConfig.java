package cn.veryai.arcreactor.actor;


import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.internal.DefaultHypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.SchedulerFactory;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.SchedulerService;
import cn.veryai.arcreactor.repo.HypervisorReadRepo;
import java.time.Duration;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PekkoConfig {
  @Bean
  ActorSystem<Void> actorSystem() {
    return ActorSystem.create(Behaviors.empty(), "cloud-resource-manager");
  }

  @Bean
  SchedulerService schedulerService(ActorSystem<Void> actorSystem, HypervisorReadRepo readMapper) {
    return SchedulerFactory.create(actorSystem, readMapper);
  }

  @Bean
  HypervisorPlacement hypervisorPlacement(ActorSystem<Void> actorSystem, SchedulerService schedulerService) {
    Duration askTimeout = Duration.ofMillis(actorSystem.settings().config()
            .getDuration("cloud.scheduler.ask-timeout").toMillis());
    return new DefaultHypervisorPlacement(actorSystem, schedulerService, askTimeout);
  }

}
