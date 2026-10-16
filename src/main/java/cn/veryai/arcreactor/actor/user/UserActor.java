package cn.veryai.arcreactor.actor.user;

import cn.veryai.arcreactor.actor.user.command.CreateShare;
import cn.veryai.arcreactor.actor.user.command.GetShare;
import cn.veryai.arcreactor.actor.user.command.CreateProject;
import cn.veryai.arcreactor.actor.user.command.GetProject;
import cn.veryai.arcreactor.actor.user.command.UserCommand;
import cn.veryai.arcreactor.actor.user.event.ShareUpdated;
import cn.veryai.arcreactor.actor.user.event.UserEvent;
import cn.veryai.arcreactor.actor.user.event.ProjectUpdated;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.model.UserShareState;
import cn.veryai.arcreactor.actor.user.model.UserShareState.Phase;
import cn.veryai.arcreactor.actor.user.model.UserState;
import cn.veryai.arcreactor.actor.user.model.UserProjectState;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
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

/** Owns the region projects and shared storage of one user. */
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
  private final Set<String> inFlight = new HashSet<>();
  private final Set<String> projectsInFlight = new HashSet<>();

  public record ContinueShare(String regionId, long generation) implements UserCommand {}
  public record ResumeShare(String regionId) implements UserCommand {}
  public record StepCompleted(String regionId, long generation, UserShareState result,
      boolean definitiveFailure, String failure) implements UserCommand {}
  private record ShareTimerKey(String regionId) {}
  public record ContinueProject(String regionId, long generation) implements UserCommand {}
  public record ResumeProject(String regionId) implements UserCommand {}
  public record ProjectStepCompleted(String regionId, long generation, UserProjectState result,
      boolean definitiveFailure, String failure) implements UserCommand {}
  private record ProjectTimerKey(String regionId) {}

  public static Behavior<UserCommand> create(String userId, OpenStackClient client,
      UserShareSettings settings) {
    return create(userId, client, settings, UserProjectSettings.defaults());
  }

  public static Behavior<UserCommand> create(String userId, OpenStackClient client,
      UserShareSettings settings, UserProjectSettings projectSettings) {
    return Behaviors.setup(context -> Behaviors.withTimers(
        timers -> new UserActor(userId, context, timers, client, settings, projectSettings)));
  }

  private UserActor(String userId, ActorContext<UserCommand> context,
      TimerScheduler<UserCommand> timers, OpenStackClient client, UserShareSettings settings,
      UserProjectSettings projectSettings) {
    super(PersistenceId.of(ENTITY_TYPE, userId));
    if (!hasText(userId)) throw new IllegalArgumentException("userId is required");
    this.userId = userId;
    this.context = context;
    this.timers = timers;
    this.client = Objects.requireNonNull(client, "client");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.projectSettings = Objects.requireNonNull(projectSettings, "projectSettings");
  }

  @Override
  public UserState emptyState() {
    return new UserState(Map.of());
  }

  @Override
  public CommandHandler<UserCommand, UserEvent, UserState> commandHandler() {
    return newCommandHandlerBuilder().forAnyState()
        .onCommand(CreateShare.class, this::onCreateShare)
        .onCommand(GetShare.class, this::onGetShare)
        .onCommand(ContinueShare.class, this::onContinueShare)
        .onCommand(StepCompleted.class, this::onStepCompleted)
        .onCommand(ResumeShare.class, this::onResumeShare)
        .onCommand(CreateProject.class, this::onCreateProject)
        .onCommand(GetProject.class, this::onGetProject)
        .onCommand(ContinueProject.class, this::onContinueProject)
        .onCommand(ResumeProject.class, this::onResumeProject)
        .onCommand(ProjectStepCompleted.class, this::onProjectStepCompleted)
        .build();
  }

  private Effect<UserEvent, UserState> onCreateProject(UserState state, CreateProject command) {
    UserProjectState existing = state.projects().get(command.regionId());
    if (existing != null) {
      command.replyTo().tell(existing.name().equals(command.name()) ? ProjectReply.from(userId, existing)
          : new ProjectReply(ProjectReply.Status.REJECTED, userId, command.regionId(), null,
              "This user already has a project specification in this region"));
      return Effect().none();
    }
    UserProjectState project = UserProjectState.requested(command.regionId(), command.name());
    return Effect().persist(new ProjectUpdated(project)).thenRun(next -> {
      command.replyTo().tell(ProjectReply.from(userId, project));
      continueProject(project);
    });
  }

  private Effect<UserEvent, UserState> onGetProject(UserState state, GetProject command) {
    UserProjectState project = state.projects().get(command.regionId());
    command.replyTo().tell(project == null
        ? new ProjectReply(ProjectReply.Status.NOT_FOUND, userId, command.regionId(), null, "project not found")
        : ProjectReply.from(userId, project));
    return Effect().none();
  }

  private Effect<UserEvent, UserState> onContinueProject(UserState state, ContinueProject command) {
    UserProjectState project = state.projects().get(command.regionId());
    if (project == null || project.terminal() || project.generation() != command.generation()
        || projectsInFlight.contains(project.regionId())) return Effect().none();
    if (project.attempts() >= projectSettings.maxAttempts()) {
      String detail = project.phase() == UserProjectState.Phase.RECONCILING
          ? "Project creation outcome remains unknown; reconciliation required"
          : "Project operation retry limit reached";
      return Effect().persist(new ProjectUpdated(project.failed(detail)));
    }
    UserProjectState attempted = project.attempted();
    return Effect().persist(new ProjectUpdated(attempted)).thenRun(next -> executeProject(attempted));
  }

  private Effect<UserEvent, UserState> onResumeProject(UserState state, ResumeProject command) {
    UserProjectState project = state.projects().get(command.regionId());
    if (project == null || project.terminal() || projectsInFlight.contains(project.regionId())) return Effect().none();
    if (project.phase() == UserProjectState.Phase.CREATING && project.attempts() > 0) {
      UserProjectState reconciling = project.reconciling();
      return Effect().persist(new ProjectUpdated(reconciling)).thenRun(next -> continueProject(reconciling));
    }
    continueProject(project);
    return Effect().none();
  }

  private Effect<UserEvent, UserState> onProjectStepCompleted(UserState state, ProjectStepCompleted command) {
    UserProjectState project = state.projects().get(command.regionId());
    if (project == null || project.terminal() || project.generation() != command.generation()) return Effect().none();
    projectsInFlight.remove(project.regionId());
    if (command.failure() != null) {
      if (command.definitiveFailure()) {
        return Effect().persist(new ProjectUpdated(project.failed(command.failure())));
      }
      if (project.phase() == UserProjectState.Phase.CREATING) {
        UserProjectState reconciling = project.reconciling();
        return Effect().persist(new ProjectUpdated(reconciling)).thenRun(next -> scheduleProject(reconciling));
      }
      scheduleProject(project);
      return Effect().none();
    }
    UserProjectState result = command.result();
    if (project.equals(result)) {
      scheduleProject(project);
      return Effect().none();
    }
    return Effect().persist(new ProjectUpdated(result)).thenRun(next -> {
      if (!result.terminal()) continueProject(result);
    });
  }

  private void executeProject(UserProjectState project) {
    projectsInFlight.add(project.regionId());
    CompletionStage<UserProjectState> operation;
    try {
      String operationId = operationId("project:" + project.regionId());
      operation = switch (project.phase()) {
        case CREATING -> client.createProject(project.regionId(), project.name(), operationId)
            .thenApply(created -> projectCreated(project, created));
        case RECONCILING -> client.findProjectByOperationId(project.regionId(), project.name(), operationId)
            .thenApply(found -> found == null ? project : projectCreated(project, found));
        case GRANTING_ADMIN_ROLE -> client.grantProjectAdminRole(project.regionId(), project.projectId())
            .thenApply(ignored -> project.ready());
        case READY, FAILED -> CompletableFuture.completedFuture(project);
      };
    } catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation.toCompletableFuture().orTimeout(
        projectSettings.operationTimeout().toMillis(), TimeUnit.MILLISECONDS), (result, error) -> {
      if (error == null) return new ProjectStepCompleted(project.regionId(), project.generation(), result, false, null);
      Throwable cause = rootCause(error);
      ResponseException response = responseCause(cause);
      boolean definitive = cause instanceof OpenStackOperationException && cause.getCause() == null;
      if (response != null) {
        int status = response.getStatus();
        definitive = status >= 400 && status < 500 && status != 408 && status != 429
            && !(project.phase() == UserProjectState.Phase.CREATING && status == 409);
      }
      String detail = cause instanceof OpenStackOperationException && cause.getCause() == null
          ? cause.getMessage() : "Project operation failed: " + cause.getClass().getSimpleName()
              + (response == null ? "" : " (HTTP " + response.getStatus() + ")");
      return new ProjectStepCompleted(project.regionId(), project.generation(), null, definitive, detail);
    });
  }

  private static UserProjectState projectCreated(UserProjectState state, org.openstack4j.model.identity.v3.Project project) {
    if (project == null || !hasText(project.getId())) throw new IllegalStateException("Keystone returned no project ID");
    return state.created(project.getId());
  }
  private void continueProject(UserProjectState project) {
    context.getSelf().tell(new ContinueProject(project.regionId(), project.generation()));
  }
  private void scheduleProject(UserProjectState project) {
    timers.startSingleTimer(new ProjectTimerKey(project.regionId()),
        new ContinueProject(project.regionId(), project.generation()), projectSettings.retryDelay());
  }

  private Effect<UserEvent, UserState> onCreateShare(UserState state, CreateShare command) {
    UserShareState existing = state.shares().get(command.spec().regionId());
    if (existing != null) {
      command.replyTo().tell(existing.spec().equals(command.spec())
          ? ShareReply.from(existing)
          : new ShareReply(ShareReply.Status.REJECTED, null,
              "This user already has a share specification in this region"));
      return Effect().none();
    }
    UserShareState requested = UserShareState.requested(command.shareId(), command.spec());
    return Effect().persist(new ShareUpdated(requested)).thenRun(next -> {
      command.replyTo().tell(ShareReply.from(requested));
      continueNow(requested);
    });
  }

  private Effect<UserEvent, UserState> onGetShare(UserState state, GetShare command) {
    UserShareState share = state.shares().get(command.regionId());
    command.replyTo().tell(share == null
        ? new ShareReply(ShareReply.Status.NOT_FOUND, null, "share not found")
        : ShareReply.from(share));
    return Effect().none();
  }

  private Effect<UserEvent, UserState> onContinueShare(UserState state, ContinueShare command) {
    UserShareState share = state.shares().get(command.regionId());
    if (share == null || share.terminal() || share.generation() != command.generation()
        || inFlight.contains(command.regionId())) return Effect().none();
    if (share.attempts() >= settings.maxPollAttempts()) {
      String detail = switch (share.phase()) {
        case WAITING_SHARE -> "Timeout waiting for share availability/export location";
        case WAITING_ACCESS -> "Timeout waiting for active access/access key";
        case RECONCILING_CREATE -> "Share creation outcome remains unknown; reconciliation required";
        case RECONCILING_ACCESS -> "Share access outcome remains unknown; reconciliation required";
        default -> "Share operation retry limit reached";
      };
      return Effect().persist(new ShareUpdated(share.failed(detail)));
    }
    UserShareState attempted = share.attempted();
    return Effect().persist(new ShareUpdated(attempted)).thenRun(next -> execute(attempted));
  }

  private Effect<UserEvent, UserState> onStepCompleted(UserState state, StepCompleted command) {
    UserShareState share = state.shares().get(command.regionId());
    if (share == null || share.terminal() || share.generation() != command.generation()) {
      return Effect().none();
    }
    inFlight.remove(command.regionId());
    if (command.failure() != null) {
      if (command.definitiveFailure()) {
        return Effect().persist(new ShareUpdated(share.failed(command.failure())));
      }
      Phase nextPhase = switch (share.phase()) {
        case CREATING -> Phase.RECONCILING_CREATE;
        case GRANTING_ACCESS -> Phase.RECONCILING_ACCESS;
        default -> share.phase();
      };
      if (nextPhase != share.phase()) {
        UserShareState reconciling = share.phase(nextPhase);
        return Effect().persist(new ShareUpdated(reconciling))
            .thenRun(next -> schedule(reconciling));
      }
      schedule(share);
      return Effect().none();
    }
    UserShareState result = command.result();
    if (result.equals(share)) {
      schedule(share);
      return Effect().none();
    }
    return Effect().persist(new ShareUpdated(result)).thenRun(next -> {
      if (!result.terminal()) {
        if (result.phase() == Phase.GRANTING_ACCESS) continueNow(result);
        else schedule(result);
      }
    });
  }

  private Effect<UserEvent, UserState> onResumeShare(UserState state, ResumeShare command) {
    UserShareState share = state.shares().get(command.regionId());
    if (share == null || share.terminal() || inFlight.contains(command.regionId())) {
      return Effect().none();
    }
    // A crash after persisting intent may have happened before or after the remote write.
    Phase nextPhase = share.attempts() == 0 ? share.phase() : switch (share.phase()) {
      case CREATING -> Phase.RECONCILING_CREATE;
      case GRANTING_ACCESS -> Phase.RECONCILING_ACCESS;
      default -> share.phase();
    };
    if (nextPhase != share.phase()) {
      UserShareState reconciling = share.phase(nextPhase);
      return Effect().persist(new ShareUpdated(reconciling)).thenRun(next -> continueNow(reconciling));
    }
    continueNow(share);
    return Effect().none();
  }

  private void execute(UserShareState share) {
    inFlight.add(share.regionId());
    CompletionStage<UserShareState> operation;
    try {
      operation = performStep(share);
    } catch (RuntimeException error) {
      operation = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(operation.toCompletableFuture()
        .orTimeout(settings.operationTimeout().toMillis(), TimeUnit.MILLISECONDS),
        (result, error) -> {
          if (error == null) return new StepCompleted(share.regionId(), share.generation(), result, false, null);
          Throwable cause = rootCause(error);
          ResponseException response = responseCause(cause);
          boolean definitive = cause instanceof InvalidShareState
              || (response != null && response.getStatus() >= 400 && response.getStatus() < 500
                  && response.getStatus() != 408 && response.getStatus() != 429
                  && !(share.phase() == Phase.GRANTING_ACCESS && response.getStatus() == 409)
                  && response.getStatus() != 404);
          // Do not include SDK responses in errors: they can contain Ceph credentials.
          String detail = cause instanceof InvalidShareState ? cause.getMessage()
              : "Share operation failed: " + cause.getClass().getSimpleName()
                  + (response == null ? "" : " (HTTP " + response.getStatus() + ")");
          return new StepCompleted(share.regionId(), share.generation(), null, definitive, detail);
        });
  }

  private CompletionStage<UserShareState> performStep(UserShareState state) {
    ShareSpec spec = state.spec();
    return switch (state.phase()) {
      case CREATING -> client.createShare(spec.regionId(), spec.osProjectId(),
            ManilaShareCreate.builder().name(spec.name()).description(spec.description())
                .size(spec.size()).shareProto(spec.shareProto()).shareType(spec.shareType())
                .metadata(Map.of(OPERATION_METADATA, operationId(state.shareId())))
                .build()).thenApply(share -> created(state, share));
      case RECONCILING_CREATE -> client.listShares(spec.regionId(), spec.osProjectId()).thenApply(shares -> {
        List<? extends Share> matches = shares.stream()
            .filter(share -> spec.osProjectId().equals(share.getProjectId()))
            .filter(share -> share.getMetadata() != null
                && operationId(state.shareId()).equals(share.getMetadata().get(OPERATION_METADATA)))
            .toList();
        if (matches.size() > 1) throw new InvalidShareState("Multiple shares match the creation operation");
        // Never blindly recreate an uncertain write, even if one lookup returns no match.
        return matches.isEmpty() ? state : created(state, matches.getFirst());
      });
      case WAITING_SHARE -> client.getShare(spec.regionId(), spec.osProjectId(), state.osShareId())
          .thenCompose(share -> {
            if (share == null) return CompletableFuture.completedFuture(state);
            if (share.getStatus() == Share.Status.ERROR || share.getStatus() == Share.Status.ERROR_DELETING
                || share.getStatus() == Share.Status.DELETING || share.getStatus() == Share.Status.UNMANAGED) {
              throw new InvalidShareState("Share entered " + share.getStatus());
            }
            if (share.getStatus() != Share.Status.AVAILABLE) return CompletableFuture.completedFuture(state);
            return client.getShareExportLocation(spec.regionId(), spec.osProjectId(), state.osShareId())
                .thenApply(exports -> {
                  ShareExportLocation location = exports.stream()
                      .filter(export -> !Boolean.TRUE.equals(export.isAdminOnly()))
                      .filter(export -> hasText(export.getId()) && hasText(export.getPath()))
                      .min(Comparator.comparing(export -> !Boolean.TRUE.equals(export.isPreferred())))
                      .orElse(null);
                  return location == null ? state : state.transition(Phase.GRANTING_ACCESS, state.osShareId(),
                      location.getId(), location.getPath(), null, null, null);
                });
          });
      case GRANTING_ACCESS -> client.grantShareAccess(
          spec.regionId(), spec.osProjectId(), state.osShareId(),
          AccessOptions.create(Access.Level.RW, Access.Type.CEPHX, spec.accessTo()))
          .thenApply(access -> accessGranted(state, access));
      case RECONCILING_ACCESS -> client.listShareAccess(
          spec.regionId(), spec.osProjectId(), state.osShareId()).thenApply(accesses -> {
        List<? extends Access> matches = accesses.stream()
            .filter(access -> access.getAccessType() == Access.Type.CEPHX
                && access.getAccessLevel() == Access.Level.RW && spec.accessTo().equals(access.getAccessTo()))
            .toList();
        if (matches.size() > 1) throw new InvalidShareState("Multiple access rules match the requested access");
        return matches.isEmpty() ? state : accessGranted(state, matches.getFirst());
      });
      case WAITING_ACCESS -> client.getShareAccess(
          spec.regionId(), spec.osProjectId(), state.osGrantAccessId())
          .thenApply(access -> access == null ? state : accessGranted(state, access));
      case READY, FAILED -> CompletableFuture.completedFuture(state);
    };
  }

  private static UserShareState created(UserShareState state, Share share) {
    if (share == null || !hasText(share.getId())) {
      throw new IllegalStateException("Manila returned no share ID");
    }
    return state.transition(Phase.WAITING_SHARE, share.getId(), null, null, null, null, null);
  }

  private static UserShareState accessGranted(UserShareState state, Access access) {
    if (access == null || !hasText(access.getId())) {
      throw new IllegalStateException("Manila returned no access ID");
    }
    if (access.getState() == Access.State.ERROR || access.getState() == Access.State.QUEUED_TO_DENY
        || access.getState() == Access.State.DENYING) {
      // Preserve the access ID even when its backend reports a failure.
      return state.transition(Phase.FAILED, state.osShareId(), state.osExportLocationId(),
          state.exportLocationPath(), access.getId(), null, "Share access entered " + access.getState());
    }
    boolean ready = access.getState() == Access.State.ACTIVE && hasText(access.getAccessKey());
    if (!ready && state.phase() == Phase.WAITING_ACCESS
        && access.getId().equals(state.osGrantAccessId())) return state;
    return state.transition(ready ? Phase.READY : Phase.WAITING_ACCESS, state.osShareId(),
        state.osExportLocationId(), state.exportLocationPath(), access.getId(),
        ready ? access.getAccessKey() : null, null);
  }

  private String operationId(String shareId) {
    return UUID.nameUUIDFromBytes((userId.length() + ":" + userId + shareId)
        .getBytes(StandardCharsets.UTF_8)).toString();
  }

  private void continueNow(UserShareState share) {
    context.getSelf().tell(new ContinueShare(share.regionId(), share.generation()));
  }

  private void schedule(UserShareState share) {
    timers.startSingleTimer(new ShareTimerKey(share.regionId()),
        new ContinueShare(share.regionId(), share.generation()),
        settings.pollInterval());
  }

  @Override
  public EventHandler<UserState, UserEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(ShareUpdated.class, (state, event) -> state.updated(event.share()))
        .onEvent(ProjectUpdated.class, (state, event) -> state.updated(event.project())).build();
  }

  @Override
  public SignalHandler<UserState> signalHandler() {
    return newSignalHandlerBuilder().onSignal(RecoveryCompleted.class, (state, signal) -> {
      state.shares().values().stream().filter(share -> !share.terminal())
          .forEach(share -> context.getSelf().tell(new ResumeShare(share.regionId())));
      state.projects().values().stream().filter(project -> !project.terminal())
          .forEach(project -> context.getSelf().tell(new ResumeProject(project.regionId())));
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
