package cn.veryai.arcreactor.actor.hypervisor;

import cn.veryai.arcreactor.actor.hypervisor.command.*;
import cn.veryai.arcreactor.actor.hypervisor.event.*;
import cn.veryai.arcreactor.actor.hypervisor.model.*;
import cn.veryai.arcreactor.actor.hypervisor.reply.*;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.javadsl.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The sole authoritative writer for one hypervisor's resources.
 */
public final class HypervisorActor
        extends EventSourcedBehavior<HypervisorCommand, HypervisorEvent, HypervisorState> {

    public static final String ENTITY_TYPE = "Hypervisor";
    public static final EntityTypeKey<HypervisorCommand> TYPE_KEY =
            EntityTypeKey.create(HypervisorCommand.class, ENTITY_TYPE);

    private final String hypervisorId;
    private final ActorContext<HypervisorCommand> context;
    private final TimerScheduler<HypervisorCommand> timers;
    private final Clock clock;

    public static Behavior<HypervisorCommand> create(String hypervisorId) {
        return create(hypervisorId, Clock.systemUTC());
    }

    static Behavior<HypervisorCommand> create(String hypervisorId, Clock clock) {
        return Behaviors.setup(context -> Behaviors.withTimers(
                timers -> new HypervisorActor(hypervisorId, context, timers, clock)));
    }

    private HypervisorActor(String hypervisorId, ActorContext<HypervisorCommand> context,
                            TimerScheduler<HypervisorCommand> timers, Clock clock) {
        super(PersistenceId.of(HypervisorRuntime.ENTITY_TYPE, hypervisorId));
        this.hypervisorId = Objects.requireNonNull(hypervisorId);
        this.context = context;
        this.timers = timers;
        this.clock = clock;
    }

    @Override
    public HypervisorState emptyState() {
        return HypervisorState.empty(hypervisorId);
    }

    @Override
    public CommandHandler<HypervisorCommand, HypervisorEvent, HypervisorState> commandHandler() {
        return newCommandHandlerBuilder().forAnyState()
                .onCommand(ReserveResources.class, this::onReserve)
                .onCommand(ConfirmReservation.class, this::onConfirm)
                .onCommand(CancelReservation.class, this::onCancel)
                .onCommand(ExpireReservation.class, this::onExpire)
                .onCommand(ReleaseResources.class, this::onRelease)
                .onCommand(UpdateCapacity.class, this::onUpdateCapacity)
                .onCommand(EnableHypervisor.class, this::onEnable)
                .onCommand(DrainHypervisor.class, this::onDrain)
                .onCommand(EnterMaintenance.class, this::onMaintenance)
                .onCommand(GetHypervisorState.class, this::onGetState)
                .build();
    }

    private Effect<HypervisorEvent, HypervisorState> onReserve(HypervisorState state, ReserveResources command) {
        Reservation existing = state.reservations().get(command.reservationId());
        if (existing != null) {
            if (sameReservation(existing, command)) {
                command.replyTo().tell(new ReserveAccepted(existing.reservationId(), existing.expiresAt(), true));
            } else {
                command.replyTo().tell(rejected(command, ReserveRejected.Reason.RESERVATION_ID_CONFLICT,
                        "reservationId belongs to different request data"));
            }
            return Effect().none();
        }
        Allocation allocation = state.allocations().get(command.instanceId());
        if (allocation != null && allocation.reservationId().equals(command.reservationId())) {
            command.replyTo().tell(new ReserveAccepted(command.reservationId(), allocation.allocatedAt(), true));
            return Effect().none();
        }
        if (state.reservationOutcomes().containsKey(command.reservationId())) {
            command.replyTo().tell(rejected(command, ReserveRejected.Reason.RESERVATION_FINALIZED,
                    "reservation was already finalized"));
            return Effect().none();
        }
        if (allocation != null) {
            command.replyTo().tell(rejected(command, ReserveRejected.Reason.INSTANCE_ALREADY_ALLOCATED,
                    "instance already has an allocation"));
            return Effect().none();
        }
        if (state.status() != HypervisorStatus.ACTIVE) {
            command.replyTo().tell(rejected(command, ReserveRejected.Reason.NOT_ACTIVE,
                    "hypervisor status is " + state.status()));
            return Effect().none();
        }
        if (command.availabilityZone() != null
                && !command.availabilityZone().equals(state.capacity().availabilityZone())) {
            command.replyTo().tell(rejected(command, ReserveRejected.Reason.INVALID_REQUEST,
                    "host no longer matches placement constraints"));
            return Effect().none();
        }
        var assigned = state.assignResources(command.resources());
        if (assigned.isEmpty()) {
            command.replyTo().tell(rejected(command, ReserveRejected.Reason.INSUFFICIENT_RESOURCE,
                    "insufficient CPU, memory, or matching GPU"));
            return Effect().none();
        }
        Instant expiresAt = clock.instant().plus(command.ttl());
        Reservation reservation = new Reservation(command.reservationId(), command.requestId(),
                command.instanceId(), assigned.orElseThrow(), expiresAt);
        return Effect().persist(new ResourcesReserved(reservation)).thenRun(nextState -> {
            scheduleExpiration(reservation);
            context.getLog().info("reservation accepted hypervisorId={} requestId={} reservationId={} "
                            + "instanceId={} resourceVersion={}", hypervisorId, command.requestId(),
                    command.reservationId(), command.instanceId(), nextState.version());
            command.replyTo().tell(new ReserveAccepted(reservation.reservationId(), expiresAt, false));
        });
    }

    private Effect<HypervisorEvent, HypervisorState> onConfirm(HypervisorState state, ConfirmReservation command) {
        Allocation allocation = state.allocations().get(command.instanceId());
        if (allocation != null && allocation.reservationId().equals(command.reservationId())) {
            command.replyTo().tell(ConfirmReply.succeeded(true));
            return Effect().none();
        }
        Reservation reservation = state.reservations().get(command.reservationId());
        if (reservation == null) {
            command.replyTo().tell(ConfirmReply.rejected("reservation not found"));
            return Effect().none();
        }
        if (!reservation.instanceId().equals(command.instanceId())) {
            command.replyTo().tell(ConfirmReply.rejected("reservation belongs to another instance"));
            return Effect().none();
        }
        Instant now = clock.instant();
        return Effect().persist(new ReservationConfirmed(reservation, now)).thenRun(next -> {
            timers.cancel(timerKey(reservation.reservationId()));
            context.getLog().info("reservation confirmed hypervisorId={} requestId={} reservationId={} "
                            + "instanceId={} resourceVersion={}", hypervisorId, reservation.requestId(),
                    reservation.reservationId(), reservation.instanceId(), next.version());
            command.replyTo().tell(ConfirmReply.succeeded(false));
        });
    }

    private Effect<HypervisorEvent, HypervisorState> onCancel(HypervisorState state, CancelReservation command) {
        Reservation reservation = state.reservations().get(command.reservationId());
        if (reservation == null) {
            command.replyTo().tell(ActionReply.succeeded(true));
            return Effect().none();
        }
        return Effect().persist(new ReservationCancelled(reservation, clock.instant())).thenRun(next -> {
            timers.cancel(timerKey(command.reservationId()));
            context.getLog().info("reservation cancelled hypervisorId={} requestId={} reservationId={} "
                            + "instanceId={} resourceVersion={}", hypervisorId, reservation.requestId(),
                    reservation.reservationId(), reservation.instanceId(), next.version());
            command.replyTo().tell(ActionReply.succeeded(false));
        });
    }

    private Effect<HypervisorEvent, HypervisorState> onExpire(HypervisorState state, ExpireReservation command) {
        Reservation reservation = state.reservations().get(command.reservationId());
        if (reservation == null || !reservation.expiresAt().equals(command.expectedExpiresAt())
                || clock.instant().isBefore(reservation.expiresAt())) {
            return Effect().none();
        }
        return Effect().persist(new ReservationExpired(reservation, clock.instant())).thenRun(next ->
                context.getLog().info("reservation expired hypervisorId={} requestId={} reservationId={} "
                                + "instanceId={} resourceVersion={}", hypervisorId, reservation.requestId(),
                        reservation.reservationId(), reservation.instanceId(), next.version()));
    }

    private Effect<HypervisorEvent, HypervisorState> onRelease(HypervisorState state, ReleaseResources command) {
        Allocation allocation = state.allocations().get(command.instanceId());
        if (allocation == null) {
            command.replyTo().tell(ReleaseReply.succeeded(true));
            return Effect().none();
        }
        List<HypervisorEvent> events = new ArrayList<>();
        events.add(new ResourcesReleased(allocation, clock.instant()));
        if (state.status() == HypervisorStatus.DRAINING && state.allocations().size() == 1) {
            events.add(new HypervisorMaintenanceEntered(clock.instant()));
        }
        return Effect().persist(events).thenRun(next -> {
            context.getLog().info("allocation released hypervisorId={} requestId={} reservationId={} "
                            + "instanceId={} resourceVersion={}", hypervisorId, allocation.requestId(),
                    allocation.reservationId(), allocation.instanceId(), next.version());
            command.replyTo().tell(ReleaseReply.succeeded(false));
        });
    }

    private Effect<HypervisorEvent, HypervisorState> onUpdateCapacity(HypervisorState state, UpdateCapacity command) {
        try {
            state.withCapacity(command.capacity());
        } catch (IllegalStateException invalid) {
            command.replyTo().tell(ActionReply.rejected(invalid.getMessage()));
            return Effect().none();
        }
        return Effect().persist(new CapacityUpdated(command.capacity(), clock.instant()))
                .thenRun(next -> command.replyTo().tell(ActionReply.succeeded(false)));
    }

    private Effect<HypervisorEvent, HypervisorState> onEnable(HypervisorState state, EnableHypervisor command) {
        if (state.status() == HypervisorStatus.ACTIVE) {
            command.replyTo().tell(ActionReply.succeeded(true));
            return Effect().none();
        }
        return Effect().persist(new HypervisorEnabled(clock.instant()))
                .thenRun(next -> command.replyTo().tell(ActionReply.succeeded(false)));
    }

    private Effect<HypervisorEvent, HypervisorState> onDrain(HypervisorState state, DrainHypervisor command) {
        if (state.status() == HypervisorStatus.DRAINING || state.status() == HypervisorStatus.MAINTENANCE) {
            command.replyTo().tell(ActionReply.succeeded(true));
            return Effect().none();
        }
        List<HypervisorEvent> events = new ArrayList<>();
        events.add(new HypervisorDrainStarted(clock.instant()));
        if (state.allocations().isEmpty()) events.add(new HypervisorMaintenanceEntered(clock.instant()));
        return Effect().persist(events).thenRun(next -> command.replyTo().tell(ActionReply.succeeded(false)));
    }

    private Effect<HypervisorEvent, HypervisorState> onMaintenance(
            HypervisorState state, EnterMaintenance command) {
        if (state.status() == HypervisorStatus.MAINTENANCE) {
            command.replyTo().tell(ActionReply.succeeded(true));
            return Effect().none();
        }
        if (!state.allocations().isEmpty()) {
            command.replyTo().tell(ActionReply.rejected("allocations must be released before maintenance"));
            return Effect().none();
        }
        return Effect().persist(new HypervisorMaintenanceEntered(clock.instant()))
                .thenRun(next -> command.replyTo().tell(ActionReply.succeeded(false)));
    }

    private Effect<HypervisorEvent, HypervisorState> onGetState(
            HypervisorState state, GetHypervisorState command) {
        command.replyTo().tell(state);
        return Effect().none();
    }

    @Override
    public EventHandler<HypervisorState, HypervisorEvent> eventHandler() {
        return newEventHandlerBuilder().forAnyState()
                .onEvent(ResourcesReserved.class, (state, event) -> state.withReservation(event.reservation()))
                .onEvent(ReservationConfirmed.class,
                        (state, event) -> state.confirm(event.reservation().reservationId(), event.allocatedAt()))
                .onEvent(ReservationCancelled.class, (state, event) -> state.removeReservation(
                        event.reservation().reservationId(), ReservationOutcome.CANCELLED))
                .onEvent(ReservationExpired.class, (state, event) -> state.removeReservation(
                        event.reservation().reservationId(), ReservationOutcome.EXPIRED))
                .onEvent(ResourcesReleased.class,
                        (state, event) -> state.removeAllocation(event.allocation().instanceId()))
                .onEvent(CapacityUpdated.class, (state, event) -> state.withCapacity(event.capacity()))
                .onEvent(HypervisorEnabled.class, (state, event) -> state.withStatus(HypervisorStatus.ACTIVE))
                .onEvent(HypervisorDrainStarted.class,
                        (state, event) -> state.withStatus(HypervisorStatus.DRAINING))
                .onEvent(HypervisorMaintenanceEntered.class,
                        (state, event) -> state.withStatus(HypervisorStatus.MAINTENANCE))
                .build();
    }

    @Override
    public SignalHandler<HypervisorState> signalHandler() {
        return newSignalHandlerBuilder().onSignal(RecoveryCompleted.class, (state, signal) -> {
            context.getLog().info("actor recovery completed hypervisorId={} resourceVersion={} reservations={}",
                    hypervisorId, state.version(), state.reservations().size());
            state.reservations().values().forEach(this::scheduleExpiration);
        }).build();
    }

    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(100, 2);
    }

    private void scheduleExpiration(Reservation reservation) {
        Duration delay = Duration.between(clock.instant(), reservation.expiresAt());
        ExpireReservation command = new ExpireReservation(reservation.reservationId(), reservation.expiresAt());
        if (delay.isNegative() || delay.isZero()) context.getSelf().tell(command);
        else timers.startSingleTimer(timerKey(reservation.reservationId()), command, delay);
    }

    private static String timerKey(String reservationId) {
        return "reservation-expiry:" + reservationId;
    }

    private static boolean sameReservation(Reservation existing, ReserveResources command) {
        return existing.requestId().equals(command.requestId())
                && existing.instanceId().equals(command.instanceId())
                && existing.resources().vcpus() == command.resources().vcpus()
                && existing.resources().memoryMb() == command.resources().memoryMb()
                && existing.resources().gpuRequest().equals(command.resources().gpuRequest());
    }

    private static ReserveRejected rejected(ReserveResources command, ReserveRejected.Reason reason, String detail) {
        return new ReserveRejected(command.reservationId(), reason, detail);
    }
}
