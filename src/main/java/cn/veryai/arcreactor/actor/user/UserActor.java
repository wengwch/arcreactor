package cn.veryai.arcreactor.actor.user;

import cn.veryai.arcreactor.actor.user.command.CreateShare;
import cn.veryai.arcreactor.actor.user.command.GetShare;
import cn.veryai.arcreactor.actor.user.command.UserCommand;
import cn.veryai.arcreactor.actor.user.event.ShareUpdated;
import cn.veryai.arcreactor.actor.user.event.UserEvent;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.model.UserShareState;
import cn.veryai.arcreactor.actor.user.model.UserShareState.Phase;
import cn.veryai.arcreactor.actor.user.model.UserState;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
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
import java.util.concurrent.Executor;
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

/** Owns shared storage for one user; creating an instance must not create another copy. */
public final class UserActor extends EventSourcedBehavior<UserCommand, UserEvent, UserState> {
  public static final String ENTITY_TYPE = "User";
  public static final EntityTypeKey<UserCommand> TYPE_KEY =
      EntityTypeKey.create(UserCommand.class, ENTITY_TYPE);

  private static final String OPERATION_METADATA = "arcreactor_share_operation_id";
  private static final Executor EXECUTOR = task -> Thread.ofVirtual().name("openstack-share").start(task);

  private final String userId;
  private final ActorContext<UserCommand> context;
  private final TimerScheduler<UserCommand> timers;
  private final OpenStackClient client;
  private final UserShareSettings settings;
  private final Set<String> inFlight = new HashSet<>();

  public record ContinueShare(String shareId, long generation) implements UserCommand {}
  public record ResumeShare(String shareId) implements UserCommand {}
  public record StepCompleted(String shareId, long generation, UserShareState result,
      boolean definitiveFailure, String failure) implements UserCommand {}

  public static Behavior<UserCommand> create(String userId, OpenStackClient client,
      UserShareSettings settings) {
    return Behaviors.setup(context -> Behaviors.withTimers(
        timers -> new UserActor(userId, context, timers, client, settings)));
  }

  private UserActor(String userId, ActorContext<UserCommand> context,
      TimerScheduler<UserCommand> timers, OpenStackClient client, UserShareSettings settings) {
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
        .build();
  }

  private Effect<UserEvent, UserState> onCreateShare(UserState state, CreateShare command) {
    UserShareState existing = state.shares().get(command.shareId());
    if (existing != null) {
      command.replyTo().tell(existing.spec().equals(command.spec())
          ? ShareReply.from(existing)
          : new ShareReply(ShareReply.Status.REJECTED, null,
              "shareId already belongs to another specification"));
      return Effect().none();
    }
    UserShareState requested = UserShareState.requested(command.shareId(), command.spec());
    return Effect().persist(new ShareUpdated(requested)).thenRun(next -> {
      command.replyTo().tell(ShareReply.from(requested));
      continueNow(requested);
    });
  }

  private Effect<UserEvent, UserState> onGetShare(UserState state, GetShare command) {
    UserShareState share = state.shares().get(command.shareId());
    command.replyTo().tell(share == null
        ? new ShareReply(ShareReply.Status.NOT_FOUND, null, "share not found")
        : ShareReply.from(share));
    return Effect().none();
  }

  private Effect<UserEvent, UserState> onContinueShare(UserState state, ContinueShare command) {
    UserShareState share = state.shares().get(command.shareId());
    if (share == null || share.terminal() || share.generation() != command.generation()
        || inFlight.contains(command.shareId())) return Effect().none();
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
    UserShareState share = state.shares().get(command.shareId());
    if (share == null || share.terminal() || share.generation() != command.generation()) {
      return Effect().none();
    }
    inFlight.remove(command.shareId());
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
    UserShareState share = state.shares().get(command.shareId());
    if (share == null || share.terminal() || inFlight.contains(command.shareId())) {
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
    inFlight.add(share.shareId());
    context.pipeToSelf(CompletableFuture.supplyAsync(() -> performStep(share), EXECUTOR)
        .orTimeout(settings.operationTimeout().toMillis(), TimeUnit.MILLISECONDS),
        (result, error) -> {
          if (error == null) return new StepCompleted(share.shareId(), share.generation(), result, false, null);
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
          return new StepCompleted(share.shareId(), share.generation(), null, definitive, detail);
        });
  }

  private UserShareState performStep(UserShareState state) {
    ShareSpec spec = state.spec();
    return switch (state.phase()) {
      case CREATING -> {
        Share share = client.createShare(spec.regionId(), spec.osProjectId(),
            ManilaShareCreate.builder().name(spec.name()).description(spec.description())
                .size(spec.size()).shareProto(spec.shareProto()).shareType(spec.shareType())
                .metadata(Map.of(OPERATION_METADATA, operationId(state.shareId())))
                .build());
        yield created(state, share);
      }
      case RECONCILING_CREATE -> {
        List<? extends Share> matches = client.listShares(spec.regionId(), spec.osProjectId()).stream()
            .filter(share -> spec.osProjectId().equals(share.getProjectId()))
            .filter(share -> share.getMetadata() != null
                && operationId(state.shareId()).equals(share.getMetadata().get(OPERATION_METADATA)))
            .toList();
        if (matches.size() > 1) throw new InvalidShareState("Multiple shares match the creation operation");
        // Never blindly recreate an uncertain write, even if one lookup returns no match.
        yield matches.isEmpty() ? state : created(state, matches.getFirst());
      }
      case WAITING_SHARE -> {
        Share share = client.getShare(spec.regionId(), spec.osProjectId(), state.osShareId());
        if (share == null) yield state;
        if (share.getStatus() == Share.Status.ERROR || share.getStatus() == Share.Status.ERROR_DELETING
            || share.getStatus() == Share.Status.DELETING || share.getStatus() == Share.Status.UNMANAGED) {
          throw new InvalidShareState("Share entered " + share.getStatus());
        }
        if (share.getStatus() != Share.Status.AVAILABLE) yield state;
        ShareExportLocation location = client.getShareExportLocation(
                spec.regionId(), spec.osProjectId(), state.osShareId()).stream()
            .filter(export -> !Boolean.TRUE.equals(export.isAdminOnly()))
            .filter(export -> hasText(export.getId()) && hasText(export.getPath()))
            .min(Comparator.comparing(export -> !Boolean.TRUE.equals(export.isPreferred())))
            .orElse(null);
        yield location == null ? state : state.transition(Phase.GRANTING_ACCESS, state.osShareId(),
            location.getId(), location.getPath(), null, null, null);
      }
      case GRANTING_ACCESS -> accessGranted(state, client.grantShareAccess(
          spec.regionId(), spec.osProjectId(), state.osShareId(),
          AccessOptions.create(Access.Level.RW, Access.Type.CEPHX, spec.accessTo())));
      case RECONCILING_ACCESS -> {
        List<? extends Access> matches = client.listShareAccess(
                spec.regionId(), spec.osProjectId(), state.osShareId()).stream()
            .filter(access -> access.getAccessType() == Access.Type.CEPHX
                && access.getAccessLevel() == Access.Level.RW && spec.accessTo().equals(access.getAccessTo()))
            .toList();
        if (matches.size() > 1) throw new InvalidShareState("Multiple access rules match the requested access");
        yield matches.isEmpty() ? state : accessGranted(state, matches.getFirst());
      }
      case WAITING_ACCESS -> {
        Access access = client.getShareAccess(spec.regionId(), spec.osProjectId(), state.osGrantAccessId());
        yield access == null ? state : accessGranted(state, access);
      }
      case READY, FAILED -> state;
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
    context.getSelf().tell(new ContinueShare(share.shareId(), share.generation()));
  }

  private void schedule(UserShareState share) {
    timers.startSingleTimer(share.shareId(), new ContinueShare(share.shareId(), share.generation()),
        settings.pollInterval());
  }

  @Override
  public EventHandler<UserState, UserEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(ShareUpdated.class, (state, event) -> state.updated(event.share())).build();
  }

  @Override
  public SignalHandler<UserState> signalHandler() {
    return newSignalHandlerBuilder().onSignal(RecoveryCompleted.class, (state, signal) ->
        state.shares().values().stream().filter(share -> !share.terminal())
            .forEach(share -> context.getSelf().tell(new ResumeShare(share.shareId())))).build();
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
