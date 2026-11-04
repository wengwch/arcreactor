package cn.veryai.arcreactor.actor.user;

import cn.veryai.arcreactor.actor.user.command.CreateShare;
import cn.veryai.arcreactor.actor.user.command.GetShare;
import cn.veryai.arcreactor.actor.user.command.CreateProject;
import cn.veryai.arcreactor.actor.user.command.GetProject;
import cn.veryai.arcreactor.actor.user.command.CreateNetwork;
import cn.veryai.arcreactor.actor.user.command.GetNetwork;
import cn.veryai.arcreactor.actor.user.command.UserCommand;
import cn.veryai.arcreactor.actor.user.event.ShareCreationRequested;
import cn.veryai.arcreactor.actor.user.event.ShareCreated;
import cn.veryai.arcreactor.actor.user.event.ShareExportBecameReady;
import cn.veryai.arcreactor.actor.user.event.ShareAccessCreated;
import cn.veryai.arcreactor.actor.user.event.ShareBecameReady;
import cn.veryai.arcreactor.actor.user.event.ShareCreationFailed;
import cn.veryai.arcreactor.actor.user.event.UserEvent;
import cn.veryai.arcreactor.actor.user.event.ProjectCreationRequested;
import cn.veryai.arcreactor.actor.user.event.ProjectBecameReady;
import cn.veryai.arcreactor.actor.user.event.ProjectCreationFailed;
import cn.veryai.arcreactor.actor.user.event.NetworkCreationRequested;
import cn.veryai.arcreactor.actor.user.event.NetworkBecameReady;
import cn.veryai.arcreactor.actor.user.event.NetworkCreationFailed;
import cn.veryai.arcreactor.actor.user.model.NetworkResources;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.model.UserShareState;
import cn.veryai.arcreactor.actor.user.model.UserShareState.Phase;
import cn.veryai.arcreactor.actor.user.model.UserState;
import cn.veryai.arcreactor.actor.user.model.UserProjectState;
import cn.veryai.arcreactor.actor.user.model.UserNetworkState;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import cn.veryai.arcreactor.actor.user.reply.NetworkReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;
import org.apache.pekko.persistence.typed.javadsl.RetentionCriteria;
import org.apache.pekko.persistence.typed.javadsl.SignalHandler;
import org.openstack4j.api.exceptions.ResponseException;
import org.openstack4j.model.manila.Access;
import org.openstack4j.model.manila.Share;
import org.openstack4j.model.manila.ShareExportLocation;
import org.openstack4j.model.manila.actions.AccessOptions;
import org.openstack4j.openstack.manila.domain.ManilaShareCreate;

/** Owns one user's cluster projects, regional networks and shared storage. */
public final class UserActor extends EventSourcedBehavior<UserCommand, UserEvent, UserState> {
  public static final String ENTITY_TYPE = "User";
  public static final EntityTypeKey<UserCommand> TYPE_KEY =
      EntityTypeKey.create(UserCommand.class, ENTITY_TYPE);

  private static final String OPERATION_METADATA = "arcreactor_share_operation_id";

  private final String userId;
  private final ActorContext<UserCommand> context;
  private final TimerScheduler<UserCommand> timers;
  private final OpenStackClient client;
  private final UserShareSettings settings;
  private final UserProjectSettings projectSettings;
  private final UserNetworkSettings networkSettings;
  private final Set<String> sharePollsInFlight = new HashSet<>();
  private String recoveryToken;

  /** Local async replies from an earlier recovery must not change the resumed workflow. */
  private record OperationCompleted(String recoveryToken, UserCommand result) implements UserCommand {}

  public record ShareCreateCompleted(String regionId, long generation, String osShareId) implements UserCommand {}
  public record ShareExportReady(String regionId, long generation, String exportId, String exportPath)
      implements UserCommand {}
  public record ShareAccessCreateCompleted(String regionId, long generation, String accessId) implements UserCommand {}
  public record ShareReady(String regionId, long generation, String accessKey) implements UserCommand {
    @Override
    public String toString() {
      return "ShareReady[regionId=" + regionId + ", generation=" + generation + ", accessKey=<redacted>]";
    }
  }
  public record ShareFailed(String regionId, long generation, String failure) implements UserCommand {}
  public record PollShare(String regionId, long generation) implements UserCommand {}
  public record SharePollPending(String regionId, long generation) implements UserCommand {}
  public record ShareCreationTimedOut(String regionId) implements UserCommand {}
  private record SharePollTimerKey(String regionId) {}
  private record ShareCreationTimerKey(String regionId) {}
  public record ProjectReady(String clusterId, long generation, String projectId) implements UserCommand {}
  public record ProjectFailed(String clusterId, long generation, String failure) implements UserCommand {}
  public record NetworkReady(String regionId, long generation, NetworkResources resources) implements UserCommand {}
  public record NetworkFailed(String regionId, long generation, String failure) implements UserCommand {}

  public static Behavior<UserCommand> create(String userId, OpenStackClient client,
      UserShareSettings settings) {
    return create(userId, client, settings, UserProjectSettings.defaults());
  }

  public static Behavior<UserCommand> create(String userId, OpenStackClient client,
      UserShareSettings settings, UserProjectSettings projectSettings) {
    return create(userId, client, settings, projectSettings, UserNetworkSettings.defaults());
  }

  public static Behavior<UserCommand> create(String userId, OpenStackClient client,
      UserShareSettings settings, UserProjectSettings projectSettings,
      UserNetworkSettings networkSettings) {
    return Behaviors.setup(context -> Behaviors.withTimers(
        timers -> new UserActor(userId, context, timers, client, settings,
            projectSettings, networkSettings)));
  }

  private UserActor(String userId, ActorContext<UserCommand> context,
      TimerScheduler<UserCommand> timers, OpenStackClient client,
      UserShareSettings settings, UserProjectSettings projectSettings, UserNetworkSettings networkSettings) {
    super(PersistenceId.of(ENTITY_TYPE, userId));
    if (!hasText(userId)) throw new IllegalArgumentException("userId is required");
    this.userId = userId;
    this.context = context;
    this.timers = timers;
    this.client = Objects.requireNonNull(client, "client");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.projectSettings = Objects.requireNonNull(projectSettings, "projectSettings");
    this.networkSettings = Objects.requireNonNull(networkSettings, "networkSettings");
  }

  @Override
  public UserState emptyState() {
    return new UserState(Map.of(), Map.of(), Map.of());
  }

  @Override
  public CommandHandler<UserCommand, UserEvent, UserState> commandHandler() {
    return newCommandHandlerBuilder().forAnyState()
        .onCommand(OperationCompleted.class, this::onOperationCompleted)
        .onCommand(CreateShare.class, this::onCreateShare)
        .onCommand(GetShare.class, this::onGetShare)
        .onCommand(ShareCreateCompleted.class, this::onShareCreated)
        .onCommand(ShareExportReady.class, this::onShareExportReady)
        .onCommand(ShareAccessCreateCompleted.class, this::onShareAccessCreated)
        .onCommand(ShareReady.class, this::onShareReady)
        .onCommand(ShareFailed.class, this::onShareFailed)
        .onCommand(PollShare.class, this::onPollShare)
        .onCommand(SharePollPending.class, this::onSharePollPending)
        .onCommand(ShareCreationTimedOut.class, this::onShareCreationTimedOut)
        .onCommand(CreateProject.class, this::onCreateProject)
        .onCommand(GetProject.class, this::onGetProject)
        .onCommand(ProjectReady.class, this::onProjectReady)
        .onCommand(ProjectFailed.class, this::onProjectFailed)
        .onCommand(CreateNetwork.class, this::onCreateNetwork)
        .onCommand(GetNetwork.class, this::onGetNetwork)
        .onCommand(NetworkReady.class, this::onNetworkReady)
        .onCommand(NetworkFailed.class, this::onNetworkFailed)
        .build();
  }

  private Effect<UserEvent, UserState> onOperationCompleted(UserState state, OperationCompleted command) {
    if (!Objects.equals(recoveryToken, command.recoveryToken())) return Effect().none();
    return switch (command.result()) {
      case ProjectReady ready -> onProjectReady(state, ready);
      case ProjectFailed failed -> onProjectFailed(state, failed);
      case NetworkReady ready -> onNetworkReady(state, ready);
      case NetworkFailed failed -> onNetworkFailed(state, failed);
      case ShareCreateCompleted created -> onShareCreated(state, created);
      case ShareExportReady ready -> onShareExportReady(state, ready);
      case ShareAccessCreateCompleted created -> onShareAccessCreated(state, created);
      case ShareReady ready -> onShareReady(state, ready);
      case ShareFailed failed -> onShareFailed(state, failed);
      case SharePollPending pending -> onSharePollPending(state, pending);
      default -> throw new IllegalArgumentException("Unexpected operation result");
    };
  }

  private <T> void pipeOperation(CompletionStage<T> operation, BiFunction<T, Throwable, UserCommand> result) {
    String token = recoveryToken;
    context.pipeToSelf(operation, (value, error) -> new OperationCompleted(token, result.apply(value, error)));
  }

  private Effect<UserEvent, UserState> onCreateNetwork(UserState state, CreateNetwork command) {
    UserNetworkState existing = state.networks().get(command.spec().regionId());
    if (existing != null) {
      if (!existing.spec().equals(command.spec())) {
        command.replyTo().tell(new NetworkReply(NetworkReply.Status.REJECTED, userId, command.spec().regionId(), null,
            "This user already has a network specification in this region"));
      } else {
        command.replyTo().tell(NetworkReply.from(userId, existing));
      }
      return Effect().none();
    }
    UserProjectState project = state.projects().get(command.clusterId());
    if (project == null || project.phase() != UserProjectState.Phase.READY) {
      command.replyTo().tell(new NetworkReply(NetworkReply.Status.REJECTED, userId,
          command.spec().regionId(), null, "The user cluster project must be READY before creating a network"));
      return Effect().none();
    }
    return Effect().persist(new NetworkCreationRequested(command.spec(), project.projectId(),
            Instant.now().plus(networkSettings.operationTimeout())))
        .thenRun(next -> {
          UserNetworkState network = next.networks().get(command.spec().regionId());
          command.replyTo().tell(NetworkReply.from(userId, network));
          executeNetwork(network);
        });
  }

  private Effect<UserEvent, UserState> onGetNetwork(UserState state, GetNetwork command) {
    UserNetworkState network = state.networks().get(command.regionId());
    command.replyTo().tell(network == null
        ? new NetworkReply(NetworkReply.Status.NOT_FOUND, userId, command.regionId(), null, "network not found")
        : NetworkReply.from(userId, network));
    return Effect().none();
  }

  private Effect<UserEvent, UserState> onNetworkReady(UserState state, NetworkReady command) {
    UserNetworkState network = state.networks().get(command.regionId());
    if (network == null || network.terminal() || network.generation() != command.generation()) return Effect().none();
    return Effect().persist(new NetworkBecameReady(network.spec(), network.projectId(), command.resources()));
  }

  private Effect<UserEvent, UserState> onNetworkFailed(UserState state, NetworkFailed command) {
    UserNetworkState network = state.networks().get(command.regionId());
    if (network == null || network.terminal() || network.generation() != command.generation()) return Effect().none();
    return Effect().persist(new NetworkCreationFailed(network.spec(), network.projectId(), command.failure()));
  }

  private void executeNetwork(UserNetworkState network) {
    Duration remaining = Duration.between(Instant.now(), network.deadline());
    if (remaining.isNegative() || remaining.isZero()) {
      context.getSelf().tell(new NetworkFailed(network.regionId(), network.generation(),
          "Network creation timed out; OpenStack outcome is unconfirmed"));
      return;
    }
    CompletionStage<NetworkResources> operation;
    try {
      String name = network.spec().name();
      operation = client.createUserNetwork(network.spec(), network.projectId(), name);
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    pipeOperation(operation.toCompletableFuture().orTimeout(
        remaining.toNanos(), TimeUnit.NANOSECONDS), (result, error) -> {
      if (error == null) {
        return result != null
            ? new NetworkReady(network.regionId(), network.generation(), result)
            : new NetworkFailed(network.regionId(), network.generation(), "Network creation returned no resources");
      }
      Throwable cause = rootCause(error);
      ResponseException response = responseCause(cause);
      String detail = cause instanceof TimeoutException
          ? "Network operation timed out; OpenStack outcome is unconfirmed"
          : cause instanceof OpenStackOperationException && cause.getCause() == null
          ? cause.getMessage() : "Network operation failed: " + cause.getClass().getSimpleName()
              + (response == null ? "" : " (HTTP " + response.getStatus() + ")");
      return new NetworkFailed(network.regionId(), network.generation(), detail);
    });
  }

  private Effect<UserEvent, UserState> onCreateProject(UserState state, CreateProject command) {
    UserProjectState existing = state.projects().get(command.clusterId());
    if (existing != null) {
      if (!existing.name().equals(command.name())) {
        command.replyTo().tell(new ProjectReply(ProjectReply.Status.REJECTED, userId, command.clusterId(), null,
            "This user already has a project specification in this cluster"));
      } else {
        command.replyTo().tell(ProjectReply.from(userId, existing));
      }
      return Effect().none();
    }
    return Effect().persist(new ProjectCreationRequested(command.clusterId(), command.name(),
            Instant.now().plus(projectSettings.operationTimeout())))
        .thenRun(next -> {
          UserProjectState project = next.projects().get(command.clusterId());
          command.replyTo().tell(ProjectReply.from(userId, project));
          executeProject(project);
        });
  }

  private Effect<UserEvent, UserState> onGetProject(UserState state, GetProject command) {
    UserProjectState project = state.projects().get(command.clusterId());
    command.replyTo().tell(project == null
        ? new ProjectReply(ProjectReply.Status.NOT_FOUND, userId, command.clusterId(), null, "project not found")
        : ProjectReply.from(userId, project));
    return Effect().none();
  }

  private Effect<UserEvent, UserState> onProjectReady(UserState state, ProjectReady command) {
    UserProjectState project = state.projects().get(command.clusterId());
    if (project == null || project.terminal() || project.generation() != command.generation()) return Effect().none();
    return Effect().persist(new ProjectBecameReady(command.clusterId(), project.name(), command.projectId()));
  }

  private Effect<UserEvent, UserState> onProjectFailed(UserState state, ProjectFailed command) {
    UserProjectState project = state.projects().get(command.clusterId());
    if (project == null || project.terminal() || project.generation() != command.generation()) return Effect().none();
    return Effect().persist(new ProjectCreationFailed(command.clusterId(), project.name(), command.failure()));
  }

  private void executeProject(UserProjectState project) {
    Duration remaining = Duration.between(Instant.now(), project.deadline());
    if (remaining.isNegative() || remaining.isZero()) {
      context.getSelf().tell(new ProjectFailed(project.clusterId(), project.generation(),
          "Project creation timed out; OpenStack outcome is unconfirmed"));
      return;
    }
    CompletionStage<String> operation;
    try {
      operation = client.createProjectWithAdminRole(
              project.clusterId(), project.name(), userId)
          .thenApply(prepared -> prepared == null ? null : prepared.getId());
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    pipeOperation(operation.toCompletableFuture().orTimeout(
        remaining.toNanos(), TimeUnit.NANOSECONDS), (result, error) -> {
      if (error == null) {
        return hasText(result)
            ? new ProjectReady(project.clusterId(), project.generation(), result)
            : new ProjectFailed(project.clusterId(), project.generation(), "Project creation returned no project ID");
      }
      Throwable cause = rootCause(error);
      ResponseException response = responseCause(cause);
      String detail = cause instanceof TimeoutException
          ? "Project operation timed out; OpenStack outcome is unconfirmed"
          : cause instanceof OpenStackOperationException && cause.getCause() == null
          ? cause.getMessage() : "Project operation failed: " + cause.getClass().getSimpleName()
              + (response == null ? "" : " (HTTP " + response.getStatus() + ")");
      return new ProjectFailed(project.clusterId(), project.generation(), detail);
    });
  }

  private Effect<UserEvent, UserState> onCreateShare(UserState state, CreateShare command) {
    UserShareState existing = state.shares().get(command.spec().regionId());
    if (existing != null) {
      if (!existing.spec().equals(command.spec())) {
        command.replyTo().tell(new ShareReply(ShareReply.Status.REJECTED, null,
            "This user already has a share specification in this region"));
      } else {
        command.replyTo().tell(ShareReply.from(existing));
      }
      return Effect().none();
    }
    return Effect().persist(new ShareCreationRequested(command.shareId(), command.spec(),
        Instant.now().plus(settings.creationTimeout()))).thenRun(next -> {
      UserShareState share = next.shares().get(command.spec().regionId());
      command.replyTo().tell(ShareReply.from(share));
      resumeShare(share);
    });
  }

  private Effect<UserEvent, UserState> onGetShare(UserState state, GetShare command) {
    UserShareState share = state.shares().get(command.regionId());
    command.replyTo().tell(share == null
        ? new ShareReply(ShareReply.Status.NOT_FOUND, null, "share not found")
        : ShareReply.from(share));
    return Effect().none();
  }

  private Effect<UserEvent, UserState> onShareCreated(UserState state, ShareCreateCompleted command) {
    UserShareState share = state.shares().get(command.regionId());
    if (!matchesShare(share, command.generation(), Phase.CREATING)) return Effect().none();
    return Effect().persist(new ShareCreated(share.shareId(), share.spec(), command.osShareId()))
        .thenRun(next -> pollShareNow(next.shares().get(command.regionId())));
  }

  private Effect<UserEvent, UserState> onShareExportReady(UserState state, ShareExportReady command) {
    UserShareState share = state.shares().get(command.regionId());
    if (!matchesShare(share, command.generation(), Phase.WAITING_EXPORT)) return Effect().none();
    return Effect().persist(new ShareExportBecameReady(share.shareId(), share.spec(), share.osShareId(),
        command.exportId(), command.exportPath())).thenRun(next -> {
      sharePollsInFlight.remove(command.regionId());
      executeShareAccessCreate(next.shares().get(command.regionId()));
    });
  }

  private Effect<UserEvent, UserState> onShareAccessCreated(UserState state, ShareAccessCreateCompleted command) {
    UserShareState share = state.shares().get(command.regionId());
    if (!matchesShare(share, command.generation(), Phase.GRANTING_ACCESS)) return Effect().none();
    return Effect().persist(new ShareAccessCreated(share.shareId(), share.spec(), share.osShareId(),
        share.osExportLocationId(), share.exportLocationPath(), command.accessId()))
        .thenRun(next -> pollShareNow(next.shares().get(command.regionId())));
  }

  private Effect<UserEvent, UserState> onShareReady(UserState state, ShareReady command) {
    UserShareState share = state.shares().get(command.regionId());
    if (!matchesShare(share, command.generation(), Phase.WAITING_ACCESS)) return Effect().none();
    return Effect().persist(new ShareBecameReady(share.shareId(), share.spec(), share.osShareId(),
        share.osExportLocationId(), share.exportLocationPath(), share.osGrantAccessId(), command.accessKey()))
        .thenRun(next -> stopSharePolling(command.regionId()));
  }

  private Effect<UserEvent, UserState> onShareFailed(UserState state, ShareFailed command) {
    UserShareState share = state.shares().get(command.regionId());
    if (share == null || share.terminal() || share.generation() != command.generation()) return Effect().none();
    return Effect().persist(new ShareCreationFailed(share.shareId(), share.spec(), share.osShareId(),
        share.osExportLocationId(), share.exportLocationPath(), share.osGrantAccessId(), command.failure()))
        .thenRun(next -> stopSharePolling(command.regionId()));
  }

  private Effect<UserEvent, UserState> onShareCreationTimedOut(UserState state, ShareCreationTimedOut command) {
    UserShareState share = state.shares().get(command.regionId());
    if (share == null || share.terminal()) return Effect().none();
    return onShareFailed(state, new ShareFailed(share.regionId(), share.generation(),
        "Share creation timed out at " + share.phase() + "; OpenStack outcome is unconfirmed"));
  }

  private Effect<UserEvent, UserState> onPollShare(UserState state, PollShare command) {
    UserShareState share = state.shares().get(command.regionId());
    if (!matchesShare(share, command.generation(), Phase.WAITING_EXPORT)
        && !matchesShare(share, command.generation(), Phase.WAITING_ACCESS)) return Effect().none();
    if (!sharePollsInFlight.add(command.regionId())) return Effect().none();
    if (share.phase() == Phase.WAITING_EXPORT) executeShareExportPoll(share);
    else executeShareAccessPoll(share);
    return Effect().none();
  }

  private Effect<UserEvent, UserState> onSharePollPending(UserState state, SharePollPending command) {
    UserShareState share = state.shares().get(command.regionId());
    if (!matchesShare(share, command.generation(), Phase.WAITING_EXPORT)
        && !matchesShare(share, command.generation(), Phase.WAITING_ACCESS)) return Effect().none();
    sharePollsInFlight.remove(command.regionId());
    timers.startSingleTimer(new SharePollTimerKey(share.regionId()),
        new PollShare(share.regionId(), share.generation()), settings.pollInterval());
    return Effect().none();
  }

  private void executeShareCreate(UserShareState share) {
    ShareSpec spec = share.spec();
    pipeShareOperation(share, () -> client.createShare(spec.regionId(), spec.osProjectId(),
        ManilaShareCreate.builder().name(spec.name()).description(spec.description())
            .size(spec.size()).shareProto(spec.shareProto()).shareType(spec.shareType())
            .metadata(Map.of(OPERATION_METADATA, operationId(share.shareId()))).build()), created -> {
      if (created == null || !hasText(created.getId())) throw new InvalidShareState("Manila returned no share ID");
      return new ShareCreateCompleted(share.regionId(), share.generation(), created.getId());
    });
  }

  private void executeShareExportPoll(UserShareState share) {
    ShareSpec spec = share.spec();
    pipeShareOperation(share, () -> client.getShare(spec.regionId(), spec.osProjectId(), share.osShareId())
        .thenCompose(current -> {
          if (current == null) return CompletableFuture.<ShareExportLocation>completedFuture(null);
          if (current.getStatus() == Share.Status.ERROR || current.getStatus() == Share.Status.ERROR_DELETING
              || current.getStatus() == Share.Status.DELETING || current.getStatus() == Share.Status.UNMANAGED) {
            throw new InvalidShareState("Share entered " + current.getStatus());
          }
          if (current.getStatus() != Share.Status.AVAILABLE) {
            return CompletableFuture.<ShareExportLocation>completedFuture(null);
          }
          return client.getShareExportLocation(spec.regionId(), spec.osProjectId(), share.osShareId())
              .thenApply(exports -> exports == null ? null : exports.stream()
                  .filter(export -> !Boolean.TRUE.equals(export.isAdminOnly()))
                  .filter(export -> hasText(export.getId()) && hasText(export.getPath()))
                  .min(Comparator.comparing(export -> !Boolean.TRUE.equals(export.isPreferred())))
                  .orElse(null));
        }), location -> location == null
            ? new SharePollPending(share.regionId(), share.generation())
            : new ShareExportReady(share.regionId(), share.generation(), location.getId(), location.getPath()));
  }

  private void executeShareAccessCreate(UserShareState share) {
    ShareSpec spec = share.spec();
    pipeShareOperation(share, () -> client.grantShareAccess(spec.regionId(), spec.osProjectId(), share.osShareId(),
        AccessOptions.create(Access.Level.RW, Access.Type.CEPHX, spec.accessTo())), access -> {
      if (access == null || !hasText(access.getId())) throw new InvalidShareState("Manila returned no access ID");
      return new ShareAccessCreateCompleted(share.regionId(), share.generation(), access.getId());
    });
  }

  private void executeShareAccessPoll(UserShareState share) {
    pipeShareOperation(share, () -> client.getShareAccess(
        share.regionId(), share.spec().osProjectId(), share.osGrantAccessId()), access -> {
      if (access == null) return new SharePollPending(share.regionId(), share.generation());
      if (!share.osGrantAccessId().equals(access.getId())) {
        throw new InvalidShareState("Manila returned a different access rule");
      }
      if (access.getState() == Access.State.ERROR || access.getState() == Access.State.QUEUED_TO_DENY
          || access.getState() == Access.State.DENYING) {
        throw new InvalidShareState("Share access entered " + access.getState());
      }
      return access.getState() == Access.State.ACTIVE && hasText(access.getAccessKey())
          ? new ShareReady(share.regionId(), share.generation(), access.getAccessKey())
          : new SharePollPending(share.regionId(), share.generation());
    });
  }

  private <T> void pipeShareOperation(UserShareState share, Supplier<CompletionStage<T>> call,
      Function<T, UserCommand> success) {
    Duration remaining = Duration.between(Instant.now(), share.deadline());
    if (remaining.isNegative() || remaining.isZero()) {
      context.getSelf().tell(new ShareCreationTimedOut(share.regionId()));
      return;
    }
    Duration timeout = remaining.compareTo(settings.operationTimeout()) < 0 ? remaining : settings.operationTimeout();
    CompletionStage<UserCommand> operation;
    try {
      operation = call.get().thenApply(success);
    } catch (RuntimeException error) {
      operation = CompletableFuture.failedFuture(error);
    }
    pipeOperation(operation.toCompletableFuture()
        .orTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS), (result, error) -> {
      if (error == null) return result;
      Throwable cause = rootCause(error);
      ResponseException response = responseCause(cause);
      if (response != null && response.getStatus() == 404
          && (share.phase() == Phase.WAITING_EXPORT || share.phase() == Phase.WAITING_ACCESS)) {
        // A newly accepted share or access rule may not yet be visible to reads.
        return new SharePollPending(share.regionId(), share.generation());
      }
      // SDK responses may contain Ceph credentials; return only controlled error details.
      String detail = cause instanceof InvalidShareState ? cause.getMessage()
          : cause instanceof TimeoutException
          ? "Share operation timed out at " + share.phase() + "; OpenStack outcome is unconfirmed"
          : "Share operation failed at " + share.phase() + ": " + cause.getClass().getSimpleName()
              + (response == null ? "" : " (HTTP " + response.getStatus() + ")");
      return new ShareFailed(share.regionId(), share.generation(), detail);
    });
  }

  private static boolean matchesShare(UserShareState share, long generation, Phase phase) {
    return share != null && share.phase() == phase && share.generation() == generation;
  }

  private void pollShareNow(UserShareState share) {
    context.getSelf().tell(new PollShare(share.regionId(), share.generation()));
  }

  private void stopSharePolling(String regionId) {
    timers.cancel(new SharePollTimerKey(regionId));
    timers.cancel(new ShareCreationTimerKey(regionId));
    sharePollsInFlight.remove(regionId);
  }

  private void resumeShare(UserShareState share) {
    Duration remaining = Duration.between(Instant.now(), share.deadline());
    if (remaining.isNegative() || remaining.isZero()) {
      context.getSelf().tell(new ShareCreationTimedOut(share.regionId()));
      return;
    }
    timers.startSingleTimer(new ShareCreationTimerKey(share.regionId()),
        new ShareCreationTimedOut(share.regionId()), remaining);
    switch (share.phase()) {
      case CREATING -> executeShareCreate(share);
      case GRANTING_ACCESS -> executeShareAccessCreate(share);
      case WAITING_EXPORT, WAITING_ACCESS -> pollShareNow(share);
      default -> {}
    }
  }

  private String operationId(String shareId) {
    return UUID.nameUUIDFromBytes((userId.length() + ":" + userId + shareId)
        .getBytes(StandardCharsets.UTF_8)).toString();
  }

  @Override
  public EventHandler<UserState, UserEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(ShareCreationRequested.class, (state, event) ->
            state.updated(UserShareState.requested(event.shareId(), event.spec(), event.deadline())))
        .onEvent(ShareCreated.class, (state, event) ->
            state.updated(state.shares().get(event.spec().regionId()).created(event.osShareId())))
        .onEvent(ShareExportBecameReady.class, (state, event) ->
            state.updated(state.shares().get(event.spec().regionId())
                .exportReady(event.osExportLocationId(), event.exportLocationPath())))
        .onEvent(ShareAccessCreated.class, (state, event) ->
            state.updated(state.shares().get(event.spec().regionId()).accessCreated(event.osGrantAccessId())))
        .onEvent(ShareBecameReady.class, (state, event) ->
            state.updated(state.shares().get(event.spec().regionId()).ready(event.accessKey())))
        .onEvent(ShareCreationFailed.class, (state, event) ->
            state.updated(state.shares().get(event.spec().regionId()).failed(event.failure())))
        .onEvent(ProjectCreationRequested.class, (state, event) ->
            state.updated(UserProjectState.requested(event.clusterId(), event.name(), event.deadline())))
        .onEvent(ProjectBecameReady.class, (state, event) ->
            state.updated(state.projects().get(event.clusterId()).ready(event.projectId())))
        .onEvent(ProjectCreationFailed.class, (state, event) ->
            state.updated(state.projects().get(event.clusterId()).failed(event.failure())))
        .onEvent(NetworkCreationRequested.class, (state, event) ->
            state.updated(UserNetworkState.requested(event.spec(), event.projectId(), event.deadline())))
        .onEvent(NetworkBecameReady.class, (state, event) ->
            state.updated(state.networks().get(event.spec().regionId()).ready(event.resources())))
        .onEvent(NetworkCreationFailed.class, (state, event) ->
            state.updated(state.networks().get(event.spec().regionId()).failed(event.failure())))
        .build();
  }

  @Override
  public SignalHandler<UserState> signalHandler() {
    return newSignalHandlerBuilder().onSignal(RecoveryCompleted.class, (state, signal) -> {
      recoveryToken = UUID.randomUUID().toString();
      sharePollsInFlight.clear();
      state.shares().values().stream().filter(share -> !share.terminal())
          .forEach(this::resumeShare);
      state.projects().values().stream().filter(project -> !project.terminal())
          .forEach(this::executeProject);
      state.networks().values().stream().filter(network -> !network.terminal())
          .forEach(this::executeNetwork);
    }).build();
  }

  @Override
  public RetentionCriteria retentionCriteria() {
    return RetentionCriteria.snapshotEvery(100, 2);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static Throwable rootCause(Throwable error) {
    while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
    return error;
  }

  private static ResponseException responseCause(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof ResponseException response) return response;
    }
    return null;
  }

  private static final class InvalidShareState extends RuntimeException {
    private InvalidShareState(String message) {
      super(message);
    }
  }
}
