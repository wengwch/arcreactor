package cn.veryai.arcreactor.actor;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorRuntime;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
class PekkoConfig {
  @Bean(destroyMethod = "terminate")
  ActorSystem<Void> actorSystem(DataSource dataSource, SqlSessionFactory sqlSessionFactory) {

    return ActorSystem.create(Behaviors.setup(context -> {
      HypervisorRuntime.initialize(context.getSystem(), dataSource, sqlSessionFactory);
      context.getLog().info("application modules initialized; waiting for cluster membership");
      return Behaviors.empty();
    }), "veryai-cloud-manager");
  }

}
