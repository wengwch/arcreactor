package cn.veryai.arcreactor.actor;

import cn.veryai.arcreactor.actor.fip.FloatingIpActor;
import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.fip.FloatingIpSettings;
import cn.veryai.arcreactor.actor.fip.command.FloatingIpCommand;
import cn.veryai.arcreactor.actor.fip.projection.FloatingIpProjection;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorActor;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.command.HypervisorCommand;
import cn.veryai.arcreactor.actor.hypervisor.projection.HypervisorProjection;
import cn.veryai.arcreactor.actor.instance.CloudInitRenderer;
import cn.veryai.arcreactor.actor.instance.InstanceWorkflowActor;
import cn.veryai.arcreactor.actor.instance.InstanceWorkflowSettings;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.projection.InstanceProjection;
import cn.veryai.arcreactor.actor.user.UserActor;
import cn.veryai.arcreactor.actor.user.UserShareSettings;
import cn.veryai.arcreactor.actor.user.command.UserCommand;
import cn.veryai.arcreactor.actor.user.projection.UserProjection;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.service.InstanceDependencyPreparer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.Getter;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.stereotype.Component;

@Component
public final class PekkoRuntime {
  @Getter private final ActorSystem<Void> actorSystem;
  @Getter private final Config config;
  private final ClusterSharding sharding;
  private final DataSource dataSource;
  private final SqlSessionFactory sqlSessionFactory;

  public PekkoRuntime(
      ActorSystem<Void> actorSystem,
      DataSource dataSource,
      SqlSessionFactory sqlSessionFactory) {
    this.actorSystem = actorSystem;
    this.dataSource = dataSource;
    this.sqlSessionFactory = sqlSessionFactory;
    this.config = new Config(actorSystem.settings().config());
    try {
      this.sharding = ClusterSharding.get(actorSystem);
    } catch (RuntimeException error) {
      closeAfterInitializationFailure(error);
      throw error;
    }
  }

  /** Registers entities after all Spring collaborators are available. */
  void initialize(
      OpenStackClient openStackClient,
      CloudInitRenderer cloudInitRenderer,
      FloatingIpForwardings floatingIpForwardings,
      InstanceDependencyPreparer dependencyPreparer,
      HypervisorPlacement hypervisorPlacement) {
    try {
      InstanceWorkflowSettings instanceSettings =
          new InstanceWorkflowSettings(
              Duration.ofMillis(config.getInstancePollInitialDelay().toMillis()),
              Duration.ofMillis(config.getInstancePollMaxDelay().toMillis()));
      UserShareSettings userSettings =
          new UserShareSettings(config.getUserSharePollInterval());
      FloatingIpSettings floatingIpSettings = new FloatingIpSettings(
          config.getFloatingIpOutcomeRetention(), config.getFloatingIpOutcomeCleanupInterval());
      sharding.init(
          Entity.of(
              HypervisorActor.TYPE_KEY, context -> HypervisorActor.create(context.getEntityId())));
      sharding.init(
          Entity.of(
              UserActor.TYPE_KEY,
              context -> UserActor.create(context.getEntityId(), openStackClient, userSettings)));
      sharding.init(
          Entity.of(
              FloatingIpActor.TYPE_KEY,
              context -> FloatingIpActor.create(context.getEntityId(), openStackClient, floatingIpSettings)));
      sharding.init(
          Entity.of(
              InstanceWorkflowActor.TYPE_KEY,
              context ->
                  InstanceWorkflowActor.create(
                      context.getEntityId(), hypervisorPlacement, openStackClient, cloudInitRenderer,
                      floatingIpForwardings, dependencyPreparer, instanceSettings)));
      if (config.isProjectionEnabled()) {
        int sliceRanges = config.getProjectionSliceRanges();
        HypervisorProjection.init(actorSystem, dataSource, sqlSessionFactory, sliceRanges);
        InstanceProjection.init(actorSystem, dataSource, sqlSessionFactory, sliceRanges);
        UserProjection.init(actorSystem, dataSource, sqlSessionFactory, sliceRanges);
        FloatingIpProjection.init(actorSystem, dataSource, sqlSessionFactory, sliceRanges);
      }
    } catch (RuntimeException error) {
      closeAfterInitializationFailure(error);
      throw error;
    }
  }

  public EntityRef<HypervisorCommand> hypervisorActor(String id) {
    return sharding.entityRefFor(HypervisorActor.TYPE_KEY, id);
  }

  public EntityRef<WorkflowCommand> instanceActor(String id) {
    return sharding.entityRefFor(InstanceWorkflowActor.TYPE_KEY, id);
  }

  public EntityRef<UserCommand> userActor(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId is required");
    }
    return sharding.entityRefFor(UserActor.TYPE_KEY, userId);
  }

  public EntityRef<FloatingIpCommand> floatingIpActor(String floatingIpId) {
    if (floatingIpId == null || floatingIpId.isBlank()) {
      throw new IllegalArgumentException("floatingIpId is required");
    }
    return sharding.entityRefFor(FloatingIpActor.TYPE_KEY, floatingIpId);
  }

  @PreDestroy
  public void close() throws Exception {
    // Injected dependencies outlive this bean; stop projections before Spring closes the pool.
    actorSystem.terminate();
    actorSystem.getWhenTerminated().toCompletableFuture().get(45, TimeUnit.SECONDS);
  }

  private void closeAfterInitializationFailure(RuntimeException error) {
    try {
      close();
    } catch (Exception shutdownError) {
      error.addSuppressed(shutdownError);
    }
  }

  /** Named access to the ActorSystem configuration without caching individual values. */
  public static final class Config {
    private final com.typesafe.config.Config source;

    private Config(com.typesafe.config.Config source) {
      this.source = source;
    }

    public Duration getAskTimeout() {
      return source.getDuration("cloud.ask-timeout");
    }

    public int getSchedulerCandidateQueryLimit() {
      return source.getInt("cloud.scheduler.candidate-query-limit");
    }

    public int getSchedulerMaxClaimAttempts() {
      return source.getInt("cloud.scheduler.max-claim-attempts");
    }

    public Duration getInstancePollInitialDelay() {
      return source.getDuration("cloud.instance.poll.initial-delay");
    }

    public Duration getInstancePollMaxDelay() {
      return source.getDuration("cloud.instance.poll.max-delay");
    }

    public Duration getUserSharePollInterval() {
      return source.getDuration("cloud.user.share.poll-interval");
    }

    public Duration getFloatingIpOutcomeRetention() {
      return source.getDuration("cloud.fip.outcome-retention");
    }

    public Duration getFloatingIpOutcomeCleanupInterval() {
      return source.getDuration("cloud.fip.outcome-cleanup-interval");
    }

    public boolean isProjectionEnabled() {
      return source.getBoolean("cloud.projection.enabled");
    }

    public int getProjectionSliceRanges() {
      return source.getInt("cloud.projection.slice-ranges");
    }
  }
}
