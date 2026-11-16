package cn.veryai.arcreactor.actor.user;

import cn.veryai.arcreactor.actor.user.command.CreateShare;
import cn.veryai.arcreactor.actor.user.command.GetShare;
import cn.veryai.arcreactor.actor.user.command.GetSystemShare;
import cn.veryai.arcreactor.actor.user.command.CreateProject;
import cn.veryai.arcreactor.actor.user.command.GetProject;
import cn.veryai.arcreactor.actor.user.command.CreateNetwork;
import cn.veryai.arcreactor.actor.user.command.GetNetwork;
import cn.veryai.arcreactor.actor.user.command.UserCommand;
import cn.veryai.arcreactor.actor.user.command.CleanUserResources;
import cn.veryai.arcreactor.actor.user.command.GetUserCleanup;
import cn.veryai.arcreactor.actor.user.event.ShareCreationRequested;
import cn.veryai.arcreactor.actor.user.event.ShareCreated;
import cn.veryai.arcreactor.actor.user.event.ShareExportBecameReady;
import cn.veryai.arcreactor.actor.user.event.ShareAccessCreated;
import cn.veryai.arcreactor.actor.user.event.ShareBecameReady;
import cn.veryai.arcreactor.actor.user.event.ShareCreationFailed;
import cn.veryai.arcreactor.actor.user.event.UserEvent;
import cn.veryai.arcreactor.actor.user.event.UserCleanupStarted;
import cn.veryai.arcreactor.actor.user.event.UserCleanupCompleted;
import cn.veryai.arcreactor.actor.user.event.UserCleanupFailed;
import cn.veryai.arcreactor.actor.user.event.UserNetworkDeleted;
import cn.veryai.arcreactor.actor.user.event.UserNetworksCleaned;
import cn.veryai.arcreactor.actor.user.event.UserShareDeleted;
import cn.veryai.arcreactor.actor.user.event.UserSharesCleaned;
import cn.veryai.arcreactor.actor.user.event.UserProjectDeleted;
import cn.veryai.arcreactor.actor.user.event.ProjectCreationRequested;
import cn.veryai.arcreactor.actor.user.event.ProjectBecameReady;
import cn.veryai.arcreactor.actor.user.event.ProjectCreationFailed;
import cn.veryai.arcreactor.actor.user.event.NetworkCreationRequested;
import cn.veryai.arcreactor.actor.user.event.NetworkBecameReady;
import cn.veryai.arcreactor.actor.user.event.NetworkCreationFailed;
import cn.veryai.arcreactor.actor.user.model.NetworkResources;
import cn.veryai.arcreactor.actor.user.model.ShareKind;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.model.UserShareNaming;
import cn.veryai.arcreactor.actor.user.model.UserShareState;
import cn.veryai.arcreactor.actor.user.model.UserShareState.Phase;
import cn.veryai.arcreactor.actor.user.model.UserState;
import cn.veryai.arcreactor.actor.user.model.UserCleanupState;
import cn.veryai.arcreactor.actor.user.model.UserProjectState;
import cn.veryai.arcreactor.actor.user.model.UserNetworkState;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import cn.veryai.arcreactor.actor.user.reply.NetworkReply;
import cn.veryai.arcreactor.actor.user.reply.UserCleanupReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
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
  private static final int MAX_SHARE_POLL_ATTEMPTS = 15;
  // Temporarily retain projects during cleanup; set true to restore project deletion.
  private static final boolean CLEANUP_PROJECT_DELETION_ENABLED = false;

  private final String userId;
  private final ActorContext<UserCommand> context;
  private final TimerScheduler<UserCommand> timers;
  private final OpenStackClient client;
  private final UserShareSettings settings;
  // Recovery starts with empty maps; repeated create commands share the current invocation.
  private final Map<String, Long> projectCreationsInFlight = new HashMap<>();
  private final Map<String, Long> networkCreationsInFlight = new HashMap<>();

  public record ShareCreateCompleted(String shareId, long generation, String osShareId)
      implements UserCommand {}
  /** Null export fields mean the share or export location is not ready yet. */
  public record ShareExportChecked(String shareId, long generation, int attempt,
      String osExportLocationId, String exportPath)
      implements UserCommand {}
  public record ShareAccessCreateCompleted(String shareId, long generation, String osGrantAccessId)
      implements UserCommand {}
  /** A null access key means the access rule is not ready yet. */
  public record ShareAccessChecked(String shareId, long generation, int attempt, String accessKey) implements UserCommand {
    @Override
    public String toString() {
      return "ShareAccessChecked[shareId=" + shareId + ", generation=" + generation
          + ", attempt=" + attempt + ", accessKey=<redacted>]";
    }
  }
  public record ShareFailed(String shareId, long generation, String failure) implements UserCommand {}
  public record PollShare(String shareId, long generation, int attempt) implements UserCommand {}
  public record ProjectReady(String clusterId, long generation, String osProjectId)
      implements UserCommand {}
  public record ProjectFailed(String clusterId, long generation, String failure)
      implements UserCommand {}
  public record NetworkReady(String regionId, long generation, NetworkResources resources)
      implements UserCommand {}
  public record NetworkFailed(String regionId, long generation, String failure)
      implements UserCommand {}
  public record CleanupDeletionSubmitted(long generation, UserCleanupState.Phase phase, String resourceId)
      implements UserCommand {}
  public record CheckCleanupDeletion(long generation, UserCleanupState.Phase phase, String resourceId)
      implements UserCommand {}
  public record CleanupDeletionChecked(long generation, UserCleanupState.Phase phase, String resourceId, boolean deleted)
      implements UserCommand {}
  public record CleanupStageCompleted(long generation, UserCleanupState.Phase phase) implements UserCommand {}
  public record CleanupDeletionFailed(long generation, UserCleanupState.Phase phase, String resourceId, String failure)
      implements UserCommand {}
  private enum CleanupTimerKey { POLL }

  public static Behavior<UserCommand> create(String userId, OpenStackClient client,
      UserShareSettings settings) {
    return Behaviors.setup(context -> Behaviors.withTimers(
        timers -> new UserActor(userId, context, timers, client, settings)));
  }

  private UserActor(String userId, ActorContext<UserCommand> context,
      TimerScheduler<UserCommand> timers, OpenStackClient client,
      UserShareSettings settings) {
    super(PersistenceId.of(ENTITY_TYPE, userId));
    if (!hasText(userId)) throw new IllegalArgumentException("userId is required");
    this.userId = userId;
    this.context = context;
    this.timers = timers;
    this.client = Objects.requireNonNull(client, "client");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  @Override
  public UserState emptyState() {
    return new UserState(Map.of(), Map.of(), Map.of(), null);
  }

  @Override
  public CommandHandler<UserCommand, UserEvent, UserState> commandHandler() {
    return newCommandHandlerBuilder().forAnyState()
        .onCommand(CleanUserResources.class, this::onCleanUserResources)
        .onCommand(GetUserCleanup.class, this::onGetUserCleanup)
        .onCommand(CleanupDeletionSubmitted.class, this::onCleanupDeletionSubmitted)
        .onCommand(CheckCleanupDeletion.class, this::onCheckCleanupDeletion)
        .onCommand(CleanupDeletionChecked.class, this::onCleanupDeletionChecked)
        .onCommand(CleanupStageCompleted.class, this::onCleanupStageCompleted)
        .onCommand(CleanupDeletionFailed.class, this::onCleanupDeletionFailed)
        .onCommand(CreateShare.class, this::onCreateShare)
        .onCommand(GetShare.class, this::onGetShare)
        .onCommand(GetSystemShare.class, this::onGetSystemShare)
        .onCommand(ShareCreateCompleted.class, this::onShareCreated)
        .onCommand(ShareExportChecked.class, this::onShareExportChecked)
        .onCommand(ShareAccessCreateCompleted.class, this::onShareAccessCreated)
        .onCommand(ShareAccessChecked.class, this::onShareAccessChecked)
        .onCommand(ShareFailed.class, this::onShareFailed)
        .onCommand(PollShare.class, this::onPollShare)
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

  private Effect<UserEvent, UserState> onCleanUserResources(UserState state, CleanUserResources command) {
    if (state.hasUnfinishedResources() || !projectCreationsInFlight.isEmpty() || !networkCreationsInFlight.isEmpty()) {
      command.replyTo().tell(cleanupReply(state, UserCleanupReply.Status.REJECTED,
          "Project, share and network creation must finish before cleanup"));
      return Effect().none();
    }
    if (state.cleanup() != null && (state.cleanup().active()
        || state.cleanup().phase() == UserCleanupState.Phase.COMPLETED
            && state.networks().isEmpty() && state.shares().isEmpty()
            && (!CLEANUP_PROJECT_DELETION_ENABLED || state.projects().isEmpty()))) {
      command.replyTo().tell(cleanupReply(state, UserCleanupReply.Status.ACCEPTED, null));
      return Effect().none();
    }
    return Effect().persist(new UserCleanupStarted(state.nextCleanupGeneration())).thenRun(next -> {
      timers.cancel(CleanupTimerKey.POLL);
      command.replyTo().tell(cleanupReply(next, UserCleanupReply.Status.ACCEPTED, null));
      continueCleanup(next);
    });
  }

  private Effect<UserEvent, UserState> onGetUserCleanup(UserState state, GetUserCleanup command) {
    command.replyTo().tell(cleanupReply(state, state.cleanup() == null
        ? UserCleanupReply.Status.NOT_STARTED : UserCleanupReply.Status.ACCEPTED, null));
    return Effect().none();
  }

  private UserCleanupReply cleanupReply(UserState state, UserCleanupReply.Status status, String detail) {
    return new UserCleanupReply(status, userId, state.cleanup(), cleanupResourceId(state),
        state.networks().size(), state.shares().size(), state.projects().size(), detail);
  }

  private static String cleanupResourceId(UserState state) {
    if (state.cleanup() == null) return null;
    return switch (state.cleanup().phase()) {
      case NETWORKS -> state.networks().keySet().stream().sorted().findFirst().orElse(null);
      case SHARES -> state.shares().keySet().stream().sorted().findFirst().orElse(null);
      case PROJECTS -> CLEANUP_PROJECT_DELETION_ENABLED
          ? state.projects().keySet().stream().sorted().findFirst().orElse(null) : null;
      case FAILED -> state.cleanup().resourceId();
      case COMPLETED -> null;
    };
  }

  private static boolean matchesCleanup(UserState state, long generation, UserCleanupState.Phase phase,
      String resourceId) {
    return state.cleanup() != null && state.cleanup().active()
        && state.cleanup().generation() == generation && state.cleanup().phase() == phase
        && Objects.equals(cleanupResourceId(state), resourceId);
  }

  private void continueCleanup(UserState state) {
    UserCleanupState cleanup = state.cleanup();
    if (cleanup == null || !cleanup.active()) return;
    String resourceId = cleanupResourceId(state);
    if (resourceId == null) {
      context.getSelf().tell(new CleanupStageCompleted(cleanup.generation(), cleanup.phase()));
      return;
    }
    CompletionStage<Void> operation;
    try {
      operation = switch (cleanup.phase()) {
        case NETWORKS -> client.deleteUserNetwork(state.networks().get(resourceId));
        case SHARES -> client.deleteUserShare(userId, state.shares().get(resourceId));
        case PROJECTS -> client.deleteUserProject(userId, state.projects().get(resourceId));
        default -> throw new IllegalStateException("Cleanup is not active");
      };
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (ignored, error) -> error == null
        ? new CleanupDeletionSubmitted(cleanup.generation(), cleanup.phase(), resourceId)
        : cleanupFailure(cleanup, resourceId, error));
  }

  private Effect<UserEvent, UserState> onCleanupDeletionSubmitted(UserState state, CleanupDeletionSubmitted command) {
    if (!matchesCleanup(state, command.generation(), command.phase(), command.resourceId())) return Effect().none();
    scheduleCleanupCheck(state.cleanup(), command.resourceId());
    return Effect().none();
  }

  private Effect<UserEvent, UserState> onCheckCleanupDeletion(UserState state, CheckCleanupDeletion command) {
    if (!matchesCleanup(state, command.generation(), command.phase(), command.resourceId())) return Effect().none();
    UserCleanupState cleanup = state.cleanup();
    CompletionStage<Boolean> operation;
    try {
      operation = switch (cleanup.phase()) {
        case NETWORKS -> client.isUserNetworkDeleted(state.networks().get(command.resourceId()));
        case SHARES -> client.isUserShareDeleted(userId, state.shares().get(command.resourceId()));
        case PROJECTS -> client.isUserProjectDeleted(userId, state.projects().get(command.resourceId()));
        default -> throw new IllegalStateException("Cleanup is not active");
      };
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (deleted, error) -> {
      if (error != null) return cleanupFailure(cleanup, command.resourceId(), error);
      if (deleted == null) return cleanupFailure(cleanup, command.resourceId(),
          new IllegalStateException("Deletion query returned no result"));
      return new CleanupDeletionChecked(cleanup.generation(), cleanup.phase(), command.resourceId(), deleted);
    });
    return Effect().none();
  }

  /** Recovery first checks remote state; it does not re-enter normal deletion submission. */
  private void recoverCleanup(UserState state) {
    UserCleanupState cleanup = state.cleanup();
    String resourceId = cleanupResourceId(state);
    if (resourceId == null) {
      context.getSelf().tell(new CleanupStageCompleted(cleanup.generation(), cleanup.phase()));
      return;
    }
    CompletionStage<Boolean> operation;
    try {
      operation = switch (cleanup.phase()) {
        case NETWORKS -> {
          UserNetworkState network = state.networks().get(resourceId);
          yield client.isUserNetworkDeleted(network).thenCompose(deleted -> deleted
              ? CompletableFuture.completedFuture(true)
              : client.deleteUserNetwork(network).thenApply(ignored -> false));
        }
        case SHARES -> {
          UserShareState share = state.shares().get(resourceId);
          yield client.isUserShareDeleted(userId, share).thenCompose(deleted -> deleted
              ? CompletableFuture.completedFuture(true)
              : client.deleteUserShare(userId, share).thenApply(ignored -> false));
        }
        case PROJECTS -> {
          UserProjectState project = state.projects().get(resourceId);
          yield client.isUserProjectDeleted(userId, project).thenCompose(deleted -> deleted
              ? CompletableFuture.completedFuture(true)
              : client.deleteUserProject(userId, project).thenApply(ignored -> false));
        }
        default -> throw new IllegalStateException("Cleanup is not active");
      };
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (deleted, error) -> error == null
        ? new CleanupDeletionChecked(cleanup.generation(), cleanup.phase(), resourceId, Boolean.TRUE.equals(deleted))
        : cleanupFailure(cleanup, resourceId, error));
  }

  private Effect<UserEvent, UserState> onCleanupDeletionChecked(UserState state, CleanupDeletionChecked command) {
    if (!matchesCleanup(state, command.generation(), command.phase(), command.resourceId())) return Effect().none();
    if (!command.deleted()) {
      scheduleCleanupCheck(state.cleanup(), command.resourceId());
      return Effect().none();
    }
    UserEvent event = switch (command.phase()) {
      case NETWORKS -> new UserNetworkDeleted(command.resourceId());
      case SHARES -> new UserShareDeleted(command.resourceId());
      case PROJECTS -> new UserProjectDeleted(command.resourceId());
      default -> throw new IllegalStateException("Cleanup is not active");
    };
    return Effect().persist(event).thenRun(this::continueCleanup);
  }

  private Effect<UserEvent, UserState> onCleanupStageCompleted(UserState state, CleanupStageCompleted command) {
    if (!matchesCleanup(state, command.generation(), command.phase(), null)) return Effect().none();
    UserEvent event = switch (command.phase()) {
      case NETWORKS -> new UserNetworksCleaned();
      case SHARES -> new UserSharesCleaned();
      case PROJECTS -> new UserCleanupCompleted();
      default -> throw new IllegalStateException("Cleanup is not active");
    };
    return Effect().persist(event).thenRun(this::continueCleanup);
  }

  private Effect<UserEvent, UserState> onCleanupDeletionFailed(UserState state, CleanupDeletionFailed command) {
    if (!matchesCleanup(state, command.generation(), command.phase(), command.resourceId())) return Effect().none();
    return Effect().persist(new UserCleanupFailed(command.resourceId(), command.failure()))
        .thenRun(next -> timers.cancel(CleanupTimerKey.POLL));
  }

  private void scheduleCleanupCheck(UserCleanupState cleanup, String resourceId) {
    timers.startSingleTimer(CleanupTimerKey.POLL,
        new CheckCleanupDeletion(cleanup.generation(), cleanup.phase(), resourceId), settings.pollInterval());
  }

  private static CleanupDeletionFailed cleanupFailure(UserCleanupState cleanup, String resourceId, Throwable error) {
    Throwable cause = rootCause(error);
    ResponseException response = responseCause(cause);
    String detail = cause instanceof OpenStackOperationException && cause.getCause() == null
        ? cause.getMessage() : "Cleanup failed: " + cause.getClass().getSimpleName()
            + (response == null ? "" : " (HTTP " + response.getStatus() + ")");
    return new CleanupDeletionFailed(cleanup.generation(), cleanup.phase(), resourceId, detail);
  }

  private Effect<UserEvent, UserState> onCreateNetwork(UserState state, CreateNetwork command) {
    if (state.cleanupBlocksCreation()) {
      command.replyTo().tell(new NetworkReply(NetworkReply.Status.REJECTED, userId, command.spec().regionId(), null,
          "User cleanup must complete before creating resources"));
      return Effect().none();
    }
    UserNetworkState existing = state.networks().get(command.spec().regionId());
    if (existing != null) {
      if (!existing.clusterId().equals(command.clusterId()) || !existing.spec().equals(command.spec())) {
        command.replyTo().tell(new NetworkReply(NetworkReply.Status.REJECTED, userId, command.spec().regionId(), null,
            "This user already has a network specification in this region"));
        return Effect().none();
      }
      if (existing.phase() != UserNetworkState.Phase.FAILED) {
        command.replyTo().tell(NetworkReply.from(userId, existing));
        if (existing.phase() == UserNetworkState.Phase.CREATING) {
          return Effect().none().thenRun(next -> executeNetwork(existing));
        }
        return Effect().none();
      }
    }
    UserProjectState project = state.projects().get(command.clusterId());
    if (project == null || project.phase() != UserProjectState.Phase.READY) {
      command.replyTo().tell(new NetworkReply(NetworkReply.Status.REJECTED, userId,
          command.spec().regionId(), null, "The user cluster project must be READY before creating a network"));
      return Effect().none();
    }
    if (existing != null && !Objects.equals(existing.osProjectId(), project.osProjectId())) {
      command.replyTo().tell(new NetworkReply(NetworkReply.Status.REJECTED, userId,
          command.spec().regionId(), null, "Network creation must use the original OpenStack project"));
      return Effect().none();
    }
    return Effect().persist(new NetworkCreationRequested(command.clusterId(), command.spec(), project.osProjectId()))
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
    networkCreationsInFlight.remove(command.regionId(), command.generation());
    if (state.cleanupBlocksCreation()) return Effect().none();
    UserNetworkState network = state.networks().get(command.regionId());
    if (network == null || network.terminal() || network.generation() != command.generation()) return Effect().none();
    return Effect().persist(new NetworkBecameReady(network.clusterId(), network.spec(), network.osProjectId(), command.resources()));
  }

  private Effect<UserEvent, UserState> onNetworkFailed(UserState state, NetworkFailed command) {
    networkCreationsInFlight.remove(command.regionId(), command.generation());
    if (state.cleanupBlocksCreation()) return Effect().none();
    UserNetworkState network = state.networks().get(command.regionId());
    if (network == null || network.terminal() || network.generation() != command.generation()) return Effect().none();
    return Effect().persist(new NetworkCreationFailed(network.clusterId(), network.spec(), network.osProjectId(), command.failure()));
  }

  private void executeNetwork(UserNetworkState network) {
    if (networkCreationsInFlight.putIfAbsent(network.regionId(), network.generation()) != null) return;
    CompletionStage<NetworkResources> operation;
    try {
      String name = network.spec().name();
      operation = client.createUserNetwork(network.spec(), network.osProjectId(), name);
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (result, error) -> {
      if (error == null) {
        return result != null
            ? new NetworkReady(network.regionId(), network.generation(), result)
            : new NetworkFailed(network.regionId(), network.generation(), "Network creation returned no resources");
      }
      Throwable cause = rootCause(error);
      ResponseException response = responseCause(cause);
      String detail = cause instanceof OpenStackOperationException && cause.getCause() == null
          ? cause.getMessage() : "Network operation failed: " + cause.getClass().getSimpleName()
              + (response == null ? "" : " (HTTP " + response.getStatus() + ")");
      return new NetworkFailed(network.regionId(), network.generation(), detail);
    });
  }

  private Effect<UserEvent, UserState> onCreateProject(UserState state, CreateProject command) {
    if (state.cleanupBlocksCreation()) {
      command.replyTo().tell(new ProjectReply(ProjectReply.Status.REJECTED, userId, command.clusterId(), null,
          "User cleanup must complete before creating resources"));
      return Effect().none();
    }
    UserProjectState existing = state.projects().get(command.clusterId());
    if (existing != null) {
      if (!existing.name().equals(command.name())) {
        command.replyTo().tell(new ProjectReply(ProjectReply.Status.REJECTED, userId, command.clusterId(), null,
            "This user already has a project specification in this cluster"));
        return Effect().none();
      }
      if (existing.phase() != UserProjectState.Phase.FAILED) {
        command.replyTo().tell(ProjectReply.from(userId, existing));
        if (existing.phase() == UserProjectState.Phase.CREATING) {
          return Effect().none().thenRun(next -> executeProject(existing));
        }
        return Effect().none();
      }
    }
    return Effect().persist(new ProjectCreationRequested(command.clusterId(), command.name()))
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
    projectCreationsInFlight.remove(command.clusterId(), command.generation());
    if (state.cleanupBlocksCreation()) return Effect().none();
    UserProjectState project = state.projects().get(command.clusterId());
    if (project == null || project.terminal() || project.generation() != command.generation()) return Effect().none();
    return Effect().persist(new ProjectBecameReady(command.clusterId(), project.name(), command.osProjectId()));
  }

  private Effect<UserEvent, UserState> onProjectFailed(UserState state, ProjectFailed command) {
    projectCreationsInFlight.remove(command.clusterId(), command.generation());
    if (state.cleanupBlocksCreation()) return Effect().none();
    UserProjectState project = state.projects().get(command.clusterId());
    if (project == null || project.terminal() || project.generation() != command.generation()) return Effect().none();
    return Effect().persist(new ProjectCreationFailed(command.clusterId(), project.name(), command.failure()));
  }

  private void executeProject(UserProjectState project) {
    if (projectCreationsInFlight.putIfAbsent(project.clusterId(), project.generation()) != null) return;
    CompletionStage<String> operation;
    try {
      operation = client.createProjectWithAdminRole(
              project.clusterId(), project.name(), userId)
          .thenApply(prepared -> prepared == null ? null : prepared.getId());
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (result, error) -> {
      if (error == null) {
        return hasText(result)
            ? new ProjectReady(project.clusterId(), project.generation(), result)
            : new ProjectFailed(project.clusterId(), project.generation(), "Project creation returned no project ID");
      }
      Throwable cause = rootCause(error);
      ResponseException response = responseCause(cause);
      String detail = cause instanceof OpenStackOperationException && cause.getCause() == null
          ? cause.getMessage() : "Project operation failed: " + cause.getClass().getSimpleName()
              + (response == null ? "" : " (HTTP " + response.getStatus() + ")");
      return new ProjectFailed(project.clusterId(), project.generation(), detail);
    });
  }

  private Effect<UserEvent, UserState> onCreateShare(UserState state, CreateShare command) {
    if (state.cleanupBlocksCreation()) {
      command.replyTo().tell(new ShareReply(ShareReply.Status.REJECTED, null,
          "User cleanup must complete before creating resources"));
      return Effect().none();
    }
    UserShareState existing = command.shareId() != null ? state.shares().get(command.shareId())
        : command.spec().kind() == ShareKind.SYSTEM
        ? state.systemShare(command.spec().regionId()) : state.userShare(command.spec().regionId());
    if (command.shareId() != null && existing == null) {
      command.replyTo().tell(new ShareReply(ShareReply.Status.REJECTED, null,
          "The share to resume does not exist"));
      return Effect().none();
    }
    if (existing != null && !existing.spec().equals(command.spec())) {
      command.replyTo().tell(new ShareReply(ShareReply.Status.REJECTED, null,
          "The existing share has a different specification"));
      return Effect().none();
    }
    if (existing != null && (command.shareId() != null || existing.phase() != Phase.FAILED)) {
      command.replyTo().tell(ShareReply.from(existing));
      // Only recovery resumes a creation already in progress.
      if (command.shareId() != null && existing.phase() == Phase.CREATING) {
        return Effect().none().thenRun(next -> advanceShare(existing));
      }
      return Effect().none();
    }
    String shareId = existing != null ? existing.shareId() : UUID.randomUUID().toString();
    return Effect().persist(new ShareCreationRequested(shareId, command.spec())).thenRun(next -> {
      UserShareState share = next.shares().get(shareId);
      command.replyTo().tell(ShareReply.from(share));
      advanceShare(share);
    });
  }

  private Effect<UserEvent, UserState> onGetShare(UserState state, GetShare command) {
    UserShareState share = state.shares().get(command.shareId());
    command.replyTo().tell(share == null
        ? new ShareReply(ShareReply.Status.NOT_FOUND, null, "share not found")
        : ShareReply.from(share));
    return Effect().none();
  }

  private Effect<UserEvent, UserState> onGetSystemShare(UserState state, GetSystemShare command) {
    UserShareState share = state.systemShare(command.regionId());
    command.replyTo().tell(share == null
        ? new ShareReply(ShareReply.Status.NOT_FOUND, null, "system share not found")
        : ShareReply.from(share));
    return Effect().none();
  }

  private Effect<UserEvent, UserState> onShareCreated(UserState state, ShareCreateCompleted command) {
    UserShareState share = state.shares().get(command.shareId());
    if (!matchesShare(state, share, command.generation()) || share.hasShare()) return Effect().none();
    return Effect().persist(new ShareCreated(share.shareId(), share.spec(), command.osShareId()))
        .thenRun(next -> advanceShare(next.shares().get(command.shareId())));
  }

  private Effect<UserEvent, UserState> onShareExportChecked(UserState state, ShareExportChecked command) {
    UserShareState share = state.shares().get(command.shareId());
    if (!matchesShare(state, share, command.generation())
        || !share.hasShare() || share.hasExportLocation()) return Effect().none();
    if (!hasText(command.osExportLocationId()) || !hasText(command.exportPath())) {
      scheduleSharePoll(share, command.attempt() + 1);
      return Effect().none();
    }
    return Effect().persist(new ShareExportBecameReady(share.shareId(), share.spec(), share.osShareId(),
        command.osExportLocationId(), command.exportPath()))
        .thenRun(next -> advanceShare(next.shares().get(command.shareId())));
  }

  private Effect<UserEvent, UserState> onShareAccessCreated(UserState state, ShareAccessCreateCompleted command) {
    UserShareState share = state.shares().get(command.shareId());
    if (!matchesShare(state, share, command.generation())
        || !share.hasExportLocation() || share.hasAccessRule()) return Effect().none();
    return Effect().persist(new ShareAccessCreated(share.shareId(), share.spec(), share.osShareId(),
        share.osExportLocationId(), share.exportLocationPath(), command.osGrantAccessId()))
        .thenRun(next -> advanceShare(next.shares().get(command.shareId())));
  }

  private Effect<UserEvent, UserState> onShareAccessChecked(UserState state, ShareAccessChecked command) {
    UserShareState share = state.shares().get(command.shareId());
    if (!matchesShare(state, share, command.generation()) || !share.hasAccessRule()) return Effect().none();
    if (!hasText(command.accessKey())) {
      scheduleSharePoll(share, command.attempt() + 1);
      return Effect().none();
    }
    return Effect().persist(new ShareBecameReady(share.shareId(), share.spec(), share.osShareId(),
        share.osExportLocationId(), share.exportLocationPath(), share.osGrantAccessId(), command.accessKey()))
        .thenRun(next -> stopSharePolling(command.shareId()));
  }

  private Effect<UserEvent, UserState> onShareFailed(UserState state, ShareFailed command) {
    UserShareState share = state.shares().get(command.shareId());
    if (!matchesShare(state, share, command.generation())) return Effect().none();
    return Effect().persist(new ShareCreationFailed(share.shareId(), share.spec(), share.osShareId(),
        share.osExportLocationId(), share.exportLocationPath(), share.osGrantAccessId(), command.failure()))
        .thenRun(next -> stopSharePolling(command.shareId()));
  }

  private Effect<UserEvent, UserState> onPollShare(UserState state, PollShare command) {
    UserShareState share = state.shares().get(command.shareId());
    if (!matchesShare(state, share, command.generation()) || !shareNeedsPolling(share)) return Effect().none();
    if (command.attempt() > MAX_SHARE_POLL_ATTEMPTS) {
      String step = share.hasExportLocation() ? "access rule" : "export location";
      return onShareFailed(state, new ShareFailed(share.shareId(), share.generation(),
          "Share " + step + " was not ready after " + MAX_SHARE_POLL_ATTEMPTS + " polling attempts"));
    }
    if (!share.hasExportLocation()) checkShareExport(share, command.attempt());
    else checkShareAccess(share, command.attempt());
    return Effect().none();
  }

  private void executeShareCreate(UserShareState share) {
    ShareSpec spec = share.spec();
    CompletionStage<String> operation;
    try {
      operation = client.createShare(spec.regionId(), spec.osProjectId(),
          ManilaShareCreate.builder()
              .name(UserShareNaming.name(userId, spec.kind(), share.shareId()))
              .description(UserShareNaming.description(userId, spec.kind(), share.shareId()))
              .size(spec.size()).shareProto(spec.shareProto()).shareType(spec.shareType()).build())
          .thenApply(created -> created == null ? null : created.getId());
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (osShareId, error) -> {
      if (error != null) return shareFailure(share, error);
      return hasText(osShareId)
          ? new ShareCreateCompleted(share.shareId(), share.generation(), osShareId)
          : new ShareFailed(share.shareId(), share.generation(), "Manila returned no share ID");
    });
  }

  private void checkShareExport(UserShareState share, int attempt) {
    ShareSpec spec = share.spec();
    CompletionStage<ShareExportLocation> operation;
    try {
      operation = client.getShare(spec.regionId(), spec.osProjectId(), share.osShareId())
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
          });
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (location, error) -> {
      if (error != null) {
        ResponseException response = responseCause(error);
        if (response != null && response.getStatus() == 404) {
          return new ShareExportChecked(share.shareId(), share.generation(), attempt, null, null);
        }
        return shareFailure(share, error);
      }
      return location == null
          ? new ShareExportChecked(share.shareId(), share.generation(), attempt, null, null)
          : new ShareExportChecked(share.shareId(), share.generation(), attempt, location.getId(), location.getPath());
    });
  }

  private void executeShareAccessCreate(UserShareState share) {
    ShareSpec spec = share.spec();
    CompletionStage<String> operation;
    try {
      operation = client.grantShareAccess(spec.regionId(), spec.osProjectId(), share.osShareId(),
          AccessOptions.create(Access.Level.RW, Access.Type.CEPHX,
              UserShareNaming.accessTo(userId, spec.kind(), share.shareId())))
          .thenApply(access -> access == null ? null : access.getId());
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (osGrantAccessId, error) -> {
      if (error != null) return shareFailure(share, error);
      return hasText(osGrantAccessId)
          ? new ShareAccessCreateCompleted(share.shareId(), share.generation(), osGrantAccessId)
          : new ShareFailed(share.shareId(), share.generation(), "Manila returned no access ID");
    });
  }

  private void checkShareAccess(UserShareState share, int attempt) {
    CompletionStage<UserCommand> operation;
    try {
      operation = client.getShareAccess(share.regionId(), share.spec().osProjectId(), share.osGrantAccessId())
          .thenApply(access -> {
            if (access == null) return new ShareAccessChecked(share.shareId(), share.generation(), attempt, null);
            if (!share.osGrantAccessId().equals(access.getId())) {
              throw new InvalidShareState("Manila returned a different access rule");
            }
            if (access.getState() == Access.State.ERROR || access.getState() == Access.State.QUEUED_TO_DENY
                || access.getState() == Access.State.DENYING) {
              throw new InvalidShareState("Share access entered " + access.getState());
            }
            return access.getState() == Access.State.ACTIVE && hasText(access.getAccessKey())
                ? new ShareAccessChecked(share.shareId(), share.generation(), attempt, access.getAccessKey())
                : new ShareAccessChecked(share.shareId(), share.generation(), attempt, null);
          });
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (result, error) -> {
      if (error == null) return result;
      ResponseException response = responseCause(error);
      if (response != null && response.getStatus() == 404) {
        return new ShareAccessChecked(share.shareId(), share.generation(), attempt, null);
      }
      return shareFailure(share, error);
    });
  }

  private static ShareFailed shareFailure(UserShareState share, Throwable error) {
    Throwable cause = rootCause(error);
    ResponseException response = responseCause(cause);
    // SDK responses may contain Ceph credentials; return only controlled error details.
    String detail = cause instanceof InvalidShareState ? cause.getMessage()
        : "Share operation failed at " + share.phase() + ": " + cause.getClass().getSimpleName()
            + (response == null ? "" : " (HTTP " + response.getStatus() + ")");
    return new ShareFailed(share.shareId(), share.generation(), detail);
  }

  private static boolean matchesShare(UserState state, UserShareState share, long generation) {
    return !state.cleanupBlocksCreation() && share != null && share.phase() == Phase.CREATING && share.generation() == generation;
  }

  private static boolean shareNeedsPolling(UserShareState share) {
    return share.hasShare() && (!share.hasExportLocation() || share.hasAccessRule());
  }

  private void scheduleSharePoll(UserShareState share, int attempt) {
    timers.startSingleTimer(share.shareId(),
        new PollShare(share.shareId(), share.generation(), attempt), settings.pollInterval());
  }

  private void stopSharePolling(String shareId) {
    timers.cancel(shareId);
  }

  private void advanceShare(UserShareState share) {
    if (share.terminal() || timers.isTimerActive(share.shareId())) return;
    if (!share.hasShare()) executeShareCreate(share);
    else if (!share.hasExportLocation()) scheduleSharePoll(share, 1);
    else if (!share.hasAccessRule()) executeShareAccessCreate(share);
    else scheduleSharePoll(share, 1);
  }

  @Override
  public EventHandler<UserState, UserEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(UserCleanupStarted.class, (state, event) ->
            state.withCleanup(UserCleanupState.started(event.generation())))
        .onEvent(UserNetworkDeleted.class, (state, event) -> state.withoutNetwork(event.regionId()))
        .onEvent(UserNetworksCleaned.class, (state, event) ->
            state.withCleanup(state.cleanup().advance(UserCleanupState.Phase.SHARES)))
        .onEvent(UserShareDeleted.class, (state, event) -> state.withoutShare(event.shareId()))
        .onEvent(UserSharesCleaned.class, (state, event) ->
            state.withCleanup(state.cleanup().advance(UserCleanupState.Phase.PROJECTS)))
        .onEvent(UserProjectDeleted.class, (state, event) -> state.withoutProject(event.clusterId()))
        .onEvent(UserCleanupCompleted.class, (state, event) ->
            state.withCleanup(state.cleanup().advance(UserCleanupState.Phase.COMPLETED)))
        .onEvent(UserCleanupFailed.class, (state, event) ->
            state.withCleanup(state.cleanup().failed(event.resourceId(), event.failure())))
        .onEvent(ShareCreationRequested.class, (state, event) -> {
          UserShareState existing = state.shares().get(event.shareId());
          return state.updated(existing == null
              ? UserShareState.requested(event.shareId(), event.spec())
              : existing.retry());
        })
        .onEvent(ShareCreated.class, (state, event) ->
            state.updated(state.shares().get(event.shareId()).created(event.osShareId())))
        .onEvent(ShareExportBecameReady.class, (state, event) ->
            state.updated(state.shares().get(event.shareId())
                .exportReady(event.osExportLocationId(), event.exportLocationPath())))
        .onEvent(ShareAccessCreated.class, (state, event) ->
            state.updated(state.shares().get(event.shareId()).accessCreated(event.osGrantAccessId())))
        .onEvent(ShareBecameReady.class, (state, event) ->
            state.updated(state.shares().get(event.shareId()).ready(event.accessKey())))
        .onEvent(ShareCreationFailed.class, (state, event) ->
            state.updated(state.shares().get(event.shareId()).failed(event.failure())))
        .onEvent(ProjectCreationRequested.class, (state, event) -> {
          UserProjectState existing = state.projects().get(event.clusterId());
          return state.updated(existing == null
              ? UserProjectState.requested(event.clusterId(), event.name(), state.creationGeneration())
              : existing.retry());
        })
        .onEvent(ProjectBecameReady.class, (state, event) ->
            state.updated(state.projects().get(event.clusterId()).ready(event.osProjectId())))
        .onEvent(ProjectCreationFailed.class, (state, event) ->
            state.updated(state.projects().get(event.clusterId()).failed(event.failure())))
        .onEvent(NetworkCreationRequested.class, (state, event) -> {
          UserNetworkState existing = state.networks().get(event.spec().regionId());
          return state.updated(existing == null
              ? UserNetworkState.requested(event.clusterId(), event.spec(), event.osProjectId(), state.creationGeneration())
              : existing.retry());
        })
        .onEvent(NetworkBecameReady.class, (state, event) ->
            state.updated(state.networks().get(event.spec().regionId()).ready(event.resources())))
        .onEvent(NetworkCreationFailed.class, (state, event) ->
            state.updated(state.networks().get(event.spec().regionId()).failed(event.failure())))
        .build();
  }

  @Override
  public SignalHandler<UserState> signalHandler() {
    return newSignalHandlerBuilder().onSignal(RecoveryCompleted.class, (state, signal) -> {
      if (state.cleanupBlocksCreation()) {
        if (state.cleanup().active()) recoverCleanup(state);
        return;
      }
      state.shares().values().stream().filter(share -> share.phase() == Phase.CREATING)
          .forEach(share -> context.getSelf().tell(new CreateShare(
              share.shareId(), share.spec(), context.getSystem().ignoreRef())));
      state.projects().values().stream().filter(project -> project.phase() == UserProjectState.Phase.CREATING)
          .forEach(project -> context.getSelf().tell(new CreateProject(
              project.clusterId(), project.name(), context.getSystem().ignoreRef())));
      state.networks().values().stream().filter(network -> network.phase() == UserNetworkState.Phase.CREATING)
          .forEach(network -> context.getSelf().tell(new CreateNetwork(
              network.clusterId(), network.spec(), context.getSystem().ignoreRef())));
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
