package cn.veryai.arcreactor.actor.hypervisor.scheduler;

import cn.veryai.arcreactor.actor.PekkoRuntime;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.internal.DefaultHypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.mybatis.HypervisorReadMapper;
import org.apache.pekko.actor.typed.ActorSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class SchedulerConfiguration {

    @Autowired private PekkoRuntime pekkoRuntime;
    @Bean
    SchedulerService schedulerService(HypervisorReadMapper readMapper) {
        return SchedulerFactory.create(pekkoRuntime.getSystem(), readMapper);
    }

    @Bean
    HypervisorPlacement hypervisorPlacement(SchedulerService schedulerService) {
        Duration askTimeout = Duration.ofMillis(pekkoRuntime.getSystem().settings().config()
                .getDuration("cloud.scheduler.ask-timeout").toMillis());
        return new DefaultHypervisorPlacement(pekkoRuntime.getSystem(), schedulerService, askTimeout);
    }
}
