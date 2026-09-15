package cn.veryai.arcreactor.actor.hypervisor.scheduler;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.internal.DefaultHypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.mybatis.HypervisorReadMapper;
import org.apache.pekko.actor.typed.ActorSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class SchedulerConfiguration {
    @Bean
    SchedulerService schedulerService(ActorSystem<?> actorSystem, HypervisorReadMapper readMapper) {
        return SchedulerFactory.create(actorSystem, readMapper);
    }

    @Bean
    HypervisorPlacement hypervisorPlacement(ActorSystem<?> actorSystem, SchedulerService schedulerService) {
        Duration askTimeout = Duration.ofMillis(actorSystem.settings().config()
                .getDuration("cloud.scheduler.ask-timeout").toMillis());
        return new DefaultHypervisorPlacement(actorSystem, schedulerService, askTimeout);
    }
}
