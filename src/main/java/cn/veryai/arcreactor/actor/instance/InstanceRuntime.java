package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.instance.projection.InstanceProjectionBootstrap;
import cn.veryai.arcreactor.openstack.NovaClient;
import com.typesafe.config.Config;
import java.time.Duration;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;

/** Public runtime facade used by the application bootstrap. */
public final class InstanceRuntime {
  private InstanceRuntime() {}

  public static void initialize(
      ActorSystem<?> system,
      HypervisorPlacement placement,
      NovaClient novaClient,
      DataSource dataSource,
      SqlSessionFactory sqlSessionFactory) {
    Config config = system.settings().config().getConfig("cloud.instance");
    InstanceWorkflowSettings settings =
        new InstanceWorkflowSettings(
            duration(system, "cloud.reservation.ttl"),
            duration(config, "provisioning-timeout"),
            duration(config, "poll.initial-delay"),
            duration(config, "poll.max-delay"));
    ClusterSharding.get(system)
        .init(
            Entity.of(
                InstanceWorkflowEntity.TYPE_KEY,
                entityContext ->
                    InstanceWorkflowActor.create(
                        entityContext.getEntityId(), placement, novaClient, settings)));
    if (system.settings().config().getBoolean("cloud.projection.enabled")) {
      InstanceProjectionBootstrap.init(
          system,
          dataSource,
          sqlSessionFactory,
          system.settings().config().getInt("cloud.projection.slice-ranges"));
    }
  }

  private static Duration duration(ActorSystem<?> system, String path) {
    return Duration.ofMillis(system.settings().config().getDuration(path).toMillis());
  }

  private static Duration duration(Config config, String path) {
    return Duration.ofMillis(config.getDuration(path).toMillis());
  }
}
