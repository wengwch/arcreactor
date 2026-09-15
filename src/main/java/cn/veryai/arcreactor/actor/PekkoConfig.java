package cn.veryai.arcreactor.actor;

import org.apache.pekko.actor.typed.ActorSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PekkoConfig {

    @Autowired
    private RootBehavior rootBehavior;

    @Bean(destroyMethod = "terminate")
    ActorSystem<Void> actorSystem() {
        return ActorSystem.create(rootBehavior.create(), "veryai-cloud-manager");
    }

}
