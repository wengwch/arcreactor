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
import cn.veryai.arcreactor.actor.user.model.UserShareNaming;
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
import java.util.Comparator;
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

  private final String userId;
  private final ActorContext<UserCommand> context;
  private final TimerScheduler<UserCommand> timers;
  private final OpenStackClient client;
  private final UserShareSettings settings;
  private final UserShareNaming shareNaming;

  public record ShareCreateCompleted(String regionId, long generation, String osShareId)
      implements UserCommand {}
  /** Null export fields mean the share or export location is not ready yet. */
  public record ShareExportChecked(String regionId, long generation,
      String osExportLocationId, String exportPath)
      implements UserCommand {}
  public record ShareAccessCreateCompleted(String regionId, long generation, String osGrantAccessId)
      implements UserCommand {}
  /** A null access key means the access rule is not ready yet. */
  public record ShareAccessChecked(String regionId, long generation, String accessKey) implements UserCommand {
    @Override
    public String toString() {
      return "ShareAccessChecked[regionId=" + regionId + ", generation=" + generation + ", accessKey=<redacted>]";
    }
  }
  public record ShareFailed(String regionId, long generation, String failure) implements UserCommand {}
  public record PollShare(String regionId, long generation) implements UserCommand {}
  private record SharePollTimerKey(String regionId) {}
  public record ProjectReady(String clusterId, long generation, String osProjectId)
      implements UserCommand {}
  public record ProjectFailed(String clusterId, long generation, String failure)
      implements UserCommand {}
  public record NetworkReady(String regionId, long generation, NetworkResources resources)
      implements UserCommand {}
  public record NetworkFailed(String regionId, long generation, String failure)
      implements UserCommand {}

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
    this.shareNaming = new UserShareNaming(userId);
    this.context = context;
    this.timers = timers;
    this.client = Objects.requireNonNull(client, "client");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  @Override
  public UserState emptyState() {
    return new UserState(Map.of(), Map.of(), Map.of());
  }

  @Override
  public CommandHandler<UserCommand, UserEvent, UserState> commandHandler() {
    return newCommandHandlerBuilder().forAnyState()
        .onCommand(CreateShare.class, this::onCreateShare)
        .onCommand(GetShare.class, this::onGetShare)
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

  private Effect<UserEvent, UserState> onCreateNetwork(UserState state, CreateNetwork command) {
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
    UserNetworkState network = state.networks().get(command.regionId());
    if (network == null || network.terminal() || network.generation() != command.generation()) return Effect().none();
    return Effect().persist(new NetworkBecameReady(network.clusterId(), network.spec(), network.osProjectId(), command.resources()));
  }

  private Effect<UserEvent, UserState> onNetworkFailed(UserState state, NetworkFailed command) {
    UserNetworkState network = state.networks().get(command.regionId());
    if (network == null || network.terminal() || network.generation() != command.generation()) return Effect().none();
    return Effect().persist(new NetworkCreationFailed(network.clusterId(), network.spec(), network.osProjectId(), command.failure()));
  }

  private void executeNetwork(UserNetworkState network) {
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
    UserProjectState project = state.projects().get(command.clusterId());
    if (project == null || project.terminal() || project.generation() != command.generation()) return Effect().none();
    return Effect().persist(new ProjectBecameReady(command.clusterId(), project.name(), command.osProjectId()));
  }

  private Effect<UserEvent, UserState> onProjectFailed(UserState state, ProjectFailed command) {
    UserProjectState project = state.projects().get(command.clusterId());
    if (project == null || project.terminal() || project.generation() != command.generation()) return Effect().none();
    return Effect().persist(new ProjectCreationFailed(command.clusterId(), project.name(), command.failure()));
  }

  private void executeProject(UserProjectState project) {
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
    UserShareState existing = state.shares().get(command.spec().regionId());
    if (existing != null) {
      if (!existing.spec().equals(command.spec())) {
        command.replyTo().tell(new ShareReply(ShareReply.Status.REJECTED, null,
            "This user already has a share specification in this region"));
        return Effect().none();
      }
      if (existing.phase() != Phase.FAILED) {
        command.replyTo().tell(ShareReply.from(existing));
        if (existing.phase() == Phase.CREATING) {
          return Effect().none().thenRun(next -> advanceShare(existing));
        }
        return Effect().none();
      }
    }
    String shareId = existing == null ? UUID.randomUUID().toString() : existing.shareId();
    return Effect().persist(new ShareCreationRequested(shareId, command.spec())).thenRun(next -> {
      UserShareState share = next.shares().get(command.spec().regionId());
      command.replyTo().tell(ShareReply.from(share));
      advanceShare(share);
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
    if (!matchesShare(share, command.generation()) || share.hasShare()) return Effect().none();
    return Effect().persist(new ShareCreated(share.shareId(), share.spec(), command.osShareId()))
        .thenRun(next -> advanceShare(next.shares().get(command.regionId())));
  }

  private Effect<UserEvent, UserState> onShareExportChecked(UserState state, ShareExportChecked command) {
    UserShareState share = state.shares().get(command.regionId());
    if (!matchesShare(share, command.generation())
        || !share.hasShare() || share.hasExportLocation()) return Effect().none();
    if (!hasText(command.osExportLocationId()) || !hasText(command.exportPath())) {
      scheduleSharePoll(share);
      return Effect().none();
    }
    return Effect().persist(new ShareExportBecameReady(share.shareId(), share.spec(), share.osShareId(),
        command.osExportLocationId(), command.exportPath()))
        .thenRun(next -> advanceShare(next.shares().get(command.regionId())));
  }

  private Effect<UserEvent, UserState> onShareAccessCreated(UserState state, ShareAccessCreateCompleted command) {
    UserShareState share = state.shares().get(command.regionId());
    if (!matchesShare(share, command.generation())
        || !share.hasExportLocation() || share.hasAccessRule()) return Effect().none();
    return Effect().persist(new ShareAccessCreated(share.shareId(), share.spec(), share.osShareId(),
        share.osExportLocationId(), share.exportLocationPath(), command.osGrantAccessId()))
        .thenRun(next -> advanceShare(next.shares().get(command.regionId())));
  }

  private Effect<UserEvent, UserState> onShareAccessChecked(UserState state, ShareAccessChecked command) {
    UserShareState share = state.shares().get(command.regionId());
    if (!matchesShare(share, command.generation()) || !share.hasAccessRule()) return Effect().none();
    if (!hasText(command.accessKey())) {
      scheduleSharePoll(share);
      return Effect().none();
    }
    return Effect().persist(new ShareBecameReady(share.shareId(), share.spec(), share.osShareId(),
        share.osExportLocationId(), share.exportLocationPath(), share.osGrantAccessId(), command.accessKey()))
        .thenRun(next -> stopSharePolling(command.regionId()));
  }

  private Effect<UserEvent, UserState> onShareFailed(UserState state, ShareFailed command) {
    UserShareState share = state.shares().get(command.regionId());
    if (!matchesShare(share, command.generation())) return Effect().none();
    return Effect().persist(new ShareCreationFailed(share.shareId(), share.spec(), share.osShareId(),
        share.osExportLocationId(), share.exportLocationPath(), share.osGrantAccessId(), command.failure()))
        .thenRun(next -> stopSharePolling(command.regionId()));
  }

  private Effect<UserEvent, UserState> onPollShare(UserState state, PollShare command) {
    UserShareState share = state.shares().get(command.regionId());
    if (!matchesShare(share, command.generation()) || !shareNeedsPolling(share)) return Effect().none();
    if (!share.hasExportLocation()) checkShareExport(share);
    else checkShareAccess(share);
    return Effect().none();
  }

  private void executeShareCreate(UserShareState share) {
    ShareSpec spec = share.spec();
    CompletionStage<String> operation;
    try {
      operation = client.createShare(spec.regionId(), spec.osProjectId(),
          ManilaShareCreate.builder().name(shareNaming.name()).description(shareNaming.description())
              .size(spec.size()).shareProto(spec.shareProto()).shareType(spec.shareType()).build())
          .thenApply(created -> created == null ? null : created.getId());
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (osShareId, error) -> {
      if (error != null) return shareFailure(share, error);
      return hasText(osShareId)
          ? new ShareCreateCompleted(share.regionId(), share.generation(), osShareId)
          : new ShareFailed(share.regionId(), share.generation(), "Manila returned no share ID");
    });
  }

  private void checkShareExport(UserShareState share) {
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
          return new ShareExportChecked(share.regionId(), share.generation(), null, null);
        }
        return shareFailure(share, error);
      }
      return location == null
          ? new ShareExportChecked(share.regionId(), share.generation(), null, null)
          : new ShareExportChecked(share.regionId(), share.generation(), location.getId(), location.getPath());
    });
  }

  private void executeShareAccessCreate(UserShareState share) {
    ShareSpec spec = share.spec();
    CompletionStage<String> operation;
    try {
      operation = client.grantShareAccess(spec.regionId(), spec.osProjectId(), share.osShareId(),
          AccessOptions.create(Access.Level.RW, Access.Type.CEPHX, shareNaming.accessTo()))
          .thenApply(access -> access == null ? null : access.getId());
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (osGrantAccessId, error) -> {
      if (error != null) return shareFailure(share, error);
      return hasText(osGrantAccessId)
          ? new ShareAccessCreateCompleted(share.regionId(), share.generation(), osGrantAccessId)
          : new ShareFailed(share.regionId(), share.generation(), "Manila returned no access ID");
    });
  }

  private void checkShareAccess(UserShareState share) {
    CompletionStage<UserCommand> operation;
    try {
      operation = client.getShareAccess(share.regionId(), share.spec().osProjectId(), share.osGrantAccessId())
          .thenApply(access -> {
            if (access == null) return new ShareAccessChecked(share.regionId(), share.generation(), null);
            if (!share.osGrantAccessId().equals(access.getId())) {
              throw new InvalidShareState("Manila returned a different access rule");
            }
            if (access.getState() == Access.State.ERROR || access.getState() == Access.State.QUEUED_TO_DENY
                || access.getState() == Access.State.DENYING) {
              throw new InvalidShareState("Share access entered " + access.getState());
            }
            return access.getState() == Access.State.ACTIVE && hasText(access.getAccessKey())
                ? new ShareAccessChecked(share.regionId(), share.generation(), access.getAccessKey())
                : new ShareAccessChecked(share.regionId(), share.generation(), null);
          });
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation, (result, error) -> {
      if (error == null) return result;
      ResponseException response = responseCause(error);
      if (response != null && response.getStatus() == 404) {
        return new ShareAccessChecked(share.regionId(), share.generation(), null);
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
    return new ShareFailed(share.regionId(), share.generation(), detail);
  }

  private static boolean matchesShare(UserShareState share, long generation) {
    return share != null && share.phase() == Phase.CREATING && share.generation() == generation;
  }

  private static boolean shareNeedsPolling(UserShareState share) {
    return share.hasShare() && (!share.hasExportLocation() || share.hasAccessRule());
  }

  private void scheduleSharePoll(UserShareState share) {
    timers.startSingleTimer(new SharePollTimerKey(share.regionId()),
        new PollShare(share.regionId(), share.generation()), settings.pollInterval());
  }

  private void stopSharePolling(String regionId) {
    timers.cancel(new SharePollTimerKey(regionId));
  }

  private void advanceShare(UserShareState share) {
    if (share.terminal()) return;
    if (!share.hasShare()) executeShareCreate(share);
    else if (!share.hasExportLocation()) scheduleSharePoll(share);
    else if (!share.hasAccessRule()) executeShareAccessCreate(share);
    else scheduleSharePoll(share);
  }

  @Override
  public EventHandler<UserState, UserEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(ShareCreationRequested.class, (state, event) -> {
          UserShareState existing = state.shares().get(event.spec().regionId());
          return state.updated(existing == null
              ? UserShareState.requested(event.shareId(), event.spec())
              : existing.retry());
        })
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
        .onEvent(ProjectCreationRequested.class, (state, event) -> {
          UserProjectState existing = state.projects().get(event.clusterId());
          return state.updated(existing == null
              ? UserProjectState.requested(event.clusterId(), event.name())
              : existing.retry());
        })
        .onEvent(ProjectBecameReady.class, (state, event) ->
            state.updated(state.projects().get(event.clusterId()).ready(event.osProjectId())))
        .onEvent(ProjectCreationFailed.class, (state, event) ->
            state.updated(state.projects().get(event.clusterId()).failed(event.failure())))
        .onEvent(NetworkCreationRequested.class, (state, event) -> {
          UserNetworkState existing = state.networks().get(event.spec().regionId());
          return state.updated(existing == null
              ? UserNetworkState.requested(event.clusterId(), event.spec(), event.osProjectId())
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
      state.shares().values().stream().filter(share -> share.phase() == Phase.CREATING)
          .forEach(share -> context.getSelf().tell(new CreateShare(
              share.spec(), context.getSystem().ignoreRef())));
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
