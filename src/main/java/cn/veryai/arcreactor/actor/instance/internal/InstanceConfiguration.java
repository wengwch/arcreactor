package cn.veryai.arcreactor.actor.instance.internal;

import cn.veryai.arcreactor.actor.instance.InstanceLifecycle;
import java.time.Duration;
import org.apache.pekko.actor.typed.ActorSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InstanceConfiguration {
    @Bean
    InstanceLifecycle instanceLifecycle(ActorSystem<?> actorSystem) {
        Duration askTimeout = Duration.ofMillis(actorSystem.settings().config()
                .getDuration("cloud.instance.ask-timeout").toMillis());
        return new DefaultInstanceLifecycle(actorSystem, askTimeout);
    }
}
