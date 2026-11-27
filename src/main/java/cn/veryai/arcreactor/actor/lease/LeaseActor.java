package cn.veryai.arcreactor.actor.lease;

import cn.veryai.arcreactor.actor.PekkoRuntime;
import cn.veryai.arcreactor.actor.instance.command.CreateInstance;
import cn.veryai.arcreactor.actor.instance.command.DeleteInstance;
import cn.veryai.arcreactor.actor.instance.command.GetInstanceState;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.reply.InstanceReply;
import cn.veryai.arcreactor.actor.lease.command.ChangeRenewalPlan;
import cn.veryai.arcreactor.actor.lease.command.ConfigureAutoRenew;
import cn.veryai.arcreactor.actor.lease.command.CreateLease;
import cn.veryai.arcreactor.actor.lease.command.GetLeaseState;
import cn.veryai.arcreactor.actor.lease.command.InstanceOperationCompleted;
import cn.veryai.arcreactor.actor.lease.command.InstanceQueryCompleted;
import cn.veryai.arcreactor.actor.lease.command.LeaseCommand;
import cn.veryai.arcreactor.actor.lease.command.OrderOperationCompleted;
import cn.veryai.arcreactor.actor.lease.command.PollLeaseProgress;
import cn.veryai.arcreactor.actor.lease.command.PriceQuoteCompleted;
import cn.veryai.arcreactor.actor.lease.command.ReconcileLease;
import cn.veryai.arcreactor.actor.lease.command.RenewLease;
import cn.veryai.arcreactor.actor.lease.command.TerminateLease;
import cn.veryai.arcreactor.actor.lease.event.AutoRenewConfigured;
import cn.veryai.arcreactor.actor.lease.event.InstanceCreationRequested;
import cn.veryai.arcreactor.actor.lease.event.LeaseCreated;
import cn.veryai.arcreactor.actor.lease.event.LeaseEvent;
import cn.veryai.arcreactor.actor.lease.event.LeaseReconciliationScheduled;
import cn.veryai.arcreactor.actor.lease.event.LeaseRetryScheduled;
import cn.veryai.arcreactor.actor.lease.event.LeaseTerminated;
import cn.veryai.arcreactor.actor.lease.event.LeaseTerminationPrepared;
import cn.veryai.arcreactor.actor.lease.event.LeaseTerminationStarted;
import cn.veryai.arcreactor.actor.lease.event.PeriodEnded;
import cn.veryai.arcreactor.actor.lease.event.PeriodStarted;
import cn.veryai.arcreactor.actor.lease.event.PurchaseLocked;
import cn.veryai.arcreactor.actor.lease.event.PurchasePriced;
import cn.veryai.arcreactor.actor.lease.event.PurchaseRequested;
import cn.veryai.arcreactor.actor.lease.event.RenewalPlanChanged;
import cn.veryai.arcreactor.actor.lease.event.RenewalStarted;
import cn.veryai.arcreactor.actor.lease.event.SettlementCompleted;
import cn.veryai.arcreactor.actor.lease.event.SettlementRequested;
import cn.veryai.arcreactor.actor.lease.model.LeasePeriod;
import cn.veryai.arcreactor.actor.lease.model.LeasePurchase;
import cn.veryai.arcreactor.actor.lease.model.LeaseSettlement;
import cn.veryai.arcreactor.actor.lease.model.LeaseState;
import cn.veryai.arcreactor.actor.lease.model.LeaseState.Phase;
import cn.veryai.arcreactor.actor.lease.model.RentalPlan;
import cn.veryai.arcreactor.actor.lease.reply.LeaseReply;
import cn.veryai.arcreactor.actor.order.command.CreateOrder;
import cn.veryai.arcreactor.actor.order.command.GetOrderState;
import cn.veryai.arcreactor.actor.order.command.OrderCommand;
import cn.veryai.arcreactor.actor.order.command.SettleOrder;
import cn.veryai.arcreactor.actor.order.model.OrderState;
import cn.veryai.arcreactor.service.RentalPriceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EffectBuilder;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;
import org.apache.pekko.persistence.typed.javadsl.RetentionCriteria;
import org.apache.pekko.persistence.typed.javadsl.SignalHandler;

/** Settles each period before freezing the next one; instance operations use the existing workflow. */
public final class LeaseActor extends EventSourcedBehavior<LeaseCommand, LeaseEvent, LeaseState> {
  public static final String ENTITY_TYPE = "lease";
  public static final EntityTypeKey<LeaseCommand> TYPE_KEY = EntityTypeKey.create(LeaseCommand.class, ENTITY_TYPE);

  private final String leaseId;
  private final ActorContext<LeaseCommand> context;
  private final TimerScheduler<LeaseCommand> timers;
  private final PekkoRuntime runtime;
  private final LeaseSettings settings;
  private final RentalPriceService priceService;
  // Each round makes sequential requests. A fresh ID also excludes callbacks from before recovery or termination.
  private String pollId;

  public static Behavior<LeaseCommand> create(String leaseId, PekkoRuntime runtime, LeaseSettings settings,
      RentalPriceService priceService) {
    return Behaviors.setup(context -> Behaviors.withTimers(
        timers -> new LeaseActor(leaseId, context, timers, runtime, settings, priceService)));
  }

  private LeaseActor(String leaseId, ActorContext<LeaseCommand> context,
      TimerScheduler<LeaseCommand> timers, PekkoRuntime runtime, LeaseSettings settings, RentalPriceService priceService) {
    super(PersistenceId.of(ENTITY_TYPE, leaseId));
    this.leaseId = leaseId;
    this.context = context;
    this.timers = timers;
    this.runtime = runtime;
    this.settings = settings;
    this.priceService = priceService;
  }

  @Override
  public LeaseState emptyState() {
    return LeaseState.empty(leaseId);
  }

  @Override
  public CommandHandler<LeaseCommand, LeaseEvent, LeaseState> commandHandler() {
    return newCommandHandlerBuilder().forAnyState()
        .onCommand(CreateLease.class, this::onCreate)
        .onCommand(GetLeaseState.class, this::onGetState)
        .onCommand(ChangeRenewalPlan.class, this::onChangePlan)
        .onCommand(ConfigureAutoRenew.class, this::onConfigureAutoRenew)
        .onCommand(RenewLease.class, this::onRenew)
        .onCommand(TerminateLease.class, this::onTerminate)
        .onCommand(PollLeaseProgress.class, this::onPollProgress)
        .onCommand(ReconcileLease.class, this::onReconcile)
        .onCommand(OrderOperationCompleted.class, this::onOrderCompleted)
        .onCommand(InstanceQueryCompleted.class, this::onInstanceQueryCompleted)
        .onCommand(InstanceOperationCompleted.class, this::onInstanceOperationCompleted)
        .onCommand(PriceQuoteCompleted.class, this::onPriceQuoteCompleted)
        .build();
  }

  private Effect<LeaseEvent, LeaseState> onCreate(LeaseState state, CreateLease command) {
    String userId = command.spec().share().userId();
    if (state.phase() != Phase.NEW) {
      boolean sameRequest = Objects.equals(state.userId(), userId)
          && Objects.equals(state.requestId(), command.requestId());
      return reply(state, command.replyTo(), sameRequest ? null : "REQUEST_CONFLICT");
    }
    LeaseCreated created = new LeaseCreated(userId, command.requestId(),
        command.spec(), command.plan(), command.autoRenew());
    return Effect().persist(List.of(created, purchaseRequested(command.plan(), 1, 1), retryScheduled()))
        .thenRun(updated -> {
          command.replyTo().tell(LeaseReply.accepted(updated));
          pollLeaseProgress(updated);
        });
  }

  private Effect<LeaseEvent, LeaseState> onGetState(LeaseState state, GetLeaseState command) {
    return Effect().reply(command.replyTo(), state);
  }

  private Effect<LeaseEvent, LeaseState> onChangePlan(LeaseState state, ChangeRenewalPlan command) {
    if (state.phase() == Phase.NEW || state.terminating()) {
      return reply(state, command.replyTo(), "LEASE_NOT_RENEWABLE");
    }
    if (state.hasPendingRenewal()) {
      return reply(state, command.replyTo(), "RENEWAL_PENDING");
    }
    if (command.targetPeriodSeq() != state.periodSeq() + 1) {
      return reply(state, command.replyTo(), "PERIOD_ALREADY_STARTED");
    }
    if (command.expectedPlanRevision() != state.planRevision()) {
      return reply(state, command.replyTo(), "PLAN_VERSION_CONFLICT");
    }
    if (command.plan().equals(state.renewalPlan())) {
      return reply(state, command.replyTo(), null);
    }
    return Effect().persist(new RenewalPlanChanged(command.plan(), state.planRevision() + 1))
        .thenRun(updated -> command.replyTo().tell(LeaseReply.accepted(updated)));
  }

  private Effect<LeaseEvent, LeaseState> onConfigureAutoRenew(LeaseState state, ConfigureAutoRenew command) {
    if (state.phase() == Phase.NEW || state.terminating()) {
      return reply(state, command.replyTo(), "LEASE_NOT_RENEWABLE");
    }
    if (state.autoRenew() == command.enabled()) {
      return reply(state, command.replyTo(), null);
    }
    return Effect().persist(List.of(new AutoRenewConfigured(command.enabled()),
        reconciliationScheduled(state, command.enabled())))
        .thenRun(updated -> command.replyTo().tell(LeaseReply.accepted(updated)));
  }

  private Effect<LeaseEvent, LeaseState> onRenew(LeaseState state, RenewLease command) {
    if (state.terminating() || state.phase() == Phase.NEW) {
      return reply(state, command.replyTo(), "LEASE_NOT_RENEWABLE");
    }
    if (state.hasPendingRenewal()) {
      return reply(state, command.replyTo(), null);
    }
    if (state.currentPeriod() != null && Instant.now().isBefore(state.currentPeriod().endsAt())) {
      return reply(state, command.replyTo(), "CURRENT_PERIOD_NOT_ENDED");
    }
    return Effect().persist(List.of(new RenewalStarted(), retryScheduled()))
        .thenRun(updated -> {
          command.replyTo().tell(LeaseReply.accepted(updated));
          pollLeaseProgress(updated);
        });
  }

  private Effect<LeaseEvent, LeaseState> onTerminate(LeaseState state, TerminateLease command) {
    if (state.phase() == Phase.NEW) {
      return reply(state, command.replyTo(), "LEASE_NOT_FOUND");
    }
    if (state.terminating()) {
      return reply(state, command.replyTo(), null);
    }
    if (state.hasPendingWork()) {
      return reply(state, command.replyTo(), "ORDER_PENDING");
    }
    return Effect().persist(List.of(new LeaseTerminationStarted(Instant.now()), retryScheduled())).thenRun(updated -> {
      command.replyTo().tell(LeaseReply.accepted(updated));
      pollId = null;
      pollLeaseProgress(updated);
    });
  }

  private PurchaseRequested purchaseRequested(RentalPlan plan, long periodSeq, long planRevision) {
    String orderId = UUID.randomUUID().toString().replace("-", "");
    return new PurchaseRequested(new LeasePurchase(orderId, periodSeq, plan,
        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), planRevision));
  }

  private List<LeaseEvent> terminationEvents(LeaseState state, Instant terminatedAt, Instant failedAt, String reason) {
    List<LeaseEvent> events = new ArrayList<>();
    LeasePeriod currentPeriod = state.currentPeriod();
    // Persist preparation and settlement together: clear the period once and keep the pending operations exclusive.
    events.add(new LeaseTerminationPrepared(terminatedAt, reason));
    if (currentPeriod != null) {
      Instant cutoff = failedAt != null && failedAt.isBefore(terminatedAt) ? failedAt : terminatedAt;
      events.add(new SettlementRequested(new LeaseSettlement(
          currentPeriod.orderId(), currentPeriod.amount(), currentPeriod.deductAt(cutoff))));
    } else if (state.phase() == Phase.PROVISIONING) {
      LeasePurchase purchase = state.pendingRenewal();
      events.add(new SettlementRequested(new LeaseSettlement(
          purchase.orderId(), purchase.amount(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))));
    }
    if (!state.instanceCreationRequested()) {
      events.add(new LeaseTerminated());
    }
    events.add(retryScheduled());
    return events;
  }

  private Effect<LeaseEvent, LeaseState> onReconcile(LeaseState state, ReconcileLease command) {
    Instant now = Instant.now();
    if (state.phase() == Phase.NEW || state.nextActionAt() == null || now.isBefore(state.nextActionAt())
        || pollId != null || timers.isTimerActive(PollLeaseProgress.class)) {
      return Effect().none();
    }
    boolean renewalDue = state.phase() == Phase.EXPIRED && state.autoRenew();
    if (!state.hasPendingWork() && (state.periodDue(now) || renewalDue)) {
      if (state.autoRenew()) {
        return Effect().persist(List.of(new RenewalStarted(), retryScheduled()))
            .thenRun(this::pollLeaseProgress);
      }
      return Effect().persist(retryScheduled()).thenRun(updated -> {
        pollId = UUID.randomUUID().toString();
        queryInstance();
      });
    }
    return Effect().persist(reconciliationScheduled(state, state.autoRenew())).thenRun(this::pollLeaseProgress);
  }

  private Effect<LeaseEvent, LeaseState> onPollProgress(LeaseState state, PollLeaseProgress command) {
    return Effect().none().thenRun(this::pollLeaseProgress);
  }

  private void pollLeaseProgress(LeaseState state) {
    if (pollId != null) {
      return;
    }
    timers.cancel(PollLeaseProgress.class);
    if (!state.hasPendingWork()) {
      return;
    }
    pollId = UUID.randomUUID().toString();
    LeaseSettlement pendingSettlement = state.pendingSettlement();
    LeasePurchase pendingRenewal = state.pendingRenewal();
    if (pendingSettlement != null) {
      askOrder(pendingSettlement.orderId(), replyTo -> new SettleOrder(state.userId(), leaseId,
          pendingSettlement.amount(), pendingSettlement.deductAmount(), replyTo));
    } else if (pendingRenewal != null && state.phase() == Phase.WAITING_LOCK) {
      askOrder(pendingRenewal.orderId(), GetOrderState::new);
    } else {
      pollInstanceProgress(state);
    }
  }

  private void pollInstanceProgress(LeaseState state) {
    if (state.phase() == Phase.TERMINATING || state.phase() == Phase.PROVISIONING
        || state.phase() == Phase.RENEWING && state.pendingSettlement() == null) {
      queryInstance();
    } else {
      scheduleNextPoll(state);
    }
  }

  private void scheduleNextPoll(LeaseState state) {
    pollId = null;
    if (state.hasPendingWork()) {
      timers.startSingleTimer(PollLeaseProgress.class, new PollLeaseProgress(), settings.retryInterval());
    }
  }

  private Effect<LeaseEvent, LeaseState> onOrderCompleted(LeaseState state, OrderOperationCompleted command) {
    if (pollId == null || !pollId.equals(command.pollId())) {
      return Effect().none();
    }
    if (command.error() != null) {
      return recordRetryFailure(state, command.error()).thenRun(this::pollInstanceProgress);
    }
    OrderState order = command.order();
    LeaseSettlement pendingSettlement = state.pendingSettlement();
    if (pendingSettlement != null && pendingSettlement.orderId().equals(order.orderId())) {
      return observeSettlementOrder(state, pendingSettlement, order).thenRun(this::pollInstanceProgress);
    }
    LeasePurchase pendingRenewal = state.pendingRenewal();
    if (pendingRenewal != null && pendingRenewal.orderId().equals(order.orderId())) {
      if (!order.initialized()) {
        if (order.error() != null) {
          return recordRetryFailure(state, order.error()).thenRun(this::pollInstanceProgress);
        }
        return Effect().none().thenRun(updated -> createOrder(updated, updated.pendingRenewal()));
      }
      return observeRenewalOrder(state, pendingRenewal, order).thenRun(this::pollInstanceProgress);
    }
    return Effect().none().thenRun(this::pollInstanceProgress);
  }

  private EffectBuilder<LeaseEvent, LeaseState> observeSettlementOrder(LeaseState state, LeaseSettlement pendingSettlement,
      OrderState order) {
    if (!order.initialized()) {
      return recordRetryFailure(state, "ORDER_NOT_FOUND");
    }
    if (!matches(state, order, pendingSettlement.amount())) {
      return recordRetryFailure(state, "ORDER_BINDING_CONFLICT");
    }
    if (order.phase() == OrderState.Phase.INSUFFICIENT_FUNDS) {
      List<LeaseEvent> events = new ArrayList<>();
      events.add(new SettlementCompleted(order.orderId()));
      if (!state.terminating()) {
        events.addAll(terminationEvents(state, Instant.now(), null, "INSUFFICIENT_FUNDS"));
      } else {
        events.add(new LeaseRetryScheduled("INSUFFICIENT_FUNDS"));
        events.add(retryScheduled());
      }
      return Effect().persist(events);
    }
    if (order.deductAmount() != null && order.deductAmount().compareTo(pendingSettlement.deductAmount()) != 0) {
      return recordRetryFailure(state, "ORDER_SETTLEMENT_CONFLICT");
    }
    if (order.phase() == OrderState.Phase.SETTLED) {
      return Effect().persist(List.of(new SettlementCompleted(order.orderId()), retryScheduled()));
    }
    return Effect().none();
  }

  private EffectBuilder<LeaseEvent, LeaseState> observeRenewalOrder(LeaseState state, LeasePurchase pendingRenewal,
      OrderState order) {
    if (!matches(state, order, pendingRenewal.amount())) {
      return recordRetryFailure(state, "ORDER_BINDING_CONFLICT");
    }
    if (order.phase() == OrderState.Phase.INSUFFICIENT_FUNDS) {
      return Effect().persist(terminationEvents(state, Instant.now(), null, "INSUFFICIENT_FUNDS"));
    }
    if (order.deductAmount() != null) {
      return recordRetryFailure(state, "ORDER_ALREADY_SETTLING");
    }
    if (order.phase() == OrderState.Phase.LOCKED) {
      return Effect().persist(List.of(new PurchaseLocked(order.orderId()), retryScheduled()));
    }
    return Effect().none();
  }

  private boolean matches(LeaseState state, OrderState order, BigDecimal amount) {
    return leaseId.equals(order.leaseId()) && Objects.equals(state.userId(), order.userId())
        && amount.compareTo(order.amount()) == 0;
  }

  private EffectBuilder<LeaseEvent, LeaseState> recordRetryFailure(LeaseState state, String error) {
    return Objects.equals(state.error(), error) ? Effect().none() : Effect().persist(new LeaseRetryScheduled(error));
  }

  private void createOrder(LeaseState state, LeasePurchase purchase) {
    if (purchase.amount().signum() > 0) {
      askOrder(purchase.orderId(), replyTo -> new CreateOrder(state.userId(), leaseId, purchase.amount(), replyTo));
      return;
    }
    String currentPollId = pollId;
    // Return the quote to the mailbox before deciding whether to create the order.
    CompletionStage<BigDecimal> quote = CompletableFuture.supplyAsync(
        () -> priceService.quote(state.instanceSpec().hypervisorType(), purchase.plan()),
        runtime.getBlockingExecutor()).orTimeout(runtime.getConfig().getAskTimeout().toMillis(), TimeUnit.MILLISECONDS);
    context.pipeToSelf(quote, (amount, error) ->
        new PriceQuoteCompleted(currentPollId, amount, describe(error)));
  }

  private Effect<LeaseEvent, LeaseState> onPriceQuoteCompleted(LeaseState state, PriceQuoteCompleted command) {
    if (pollId == null || !pollId.equals(command.pollId())) {
      return Effect().none();
    }
    if (command.error() != null) {
      return recordRetryFailure(state, command.error()).thenRun(this::pollInstanceProgress);
    }
    return Effect().persist(new PurchasePriced(state.pendingRenewal().orderId(), command.amount()))
        .thenRun(updated -> createOrder(updated, updated.pendingRenewal()));
  }

  private void askOrder(String orderId, Function<ActorRef<OrderState>, OrderCommand> commandFactory) {
    String currentPollId = pollId;
    CompletionStage<OrderState> call = AskPattern.ask(runtime.orderActor(orderId), commandFactory::apply,
        runtime.getConfig().getAskTimeout(), context.getSystem().scheduler());
    context.pipeToSelf(call, (order, error) ->
        new OrderOperationCompleted(currentPollId, order, describe(error)));
  }

  private void queryInstance() {
    String currentPollId = pollId;
    CompletionStage<InstanceWorkflowState> call = AskPattern.ask(runtime.instanceActor(leaseId), GetInstanceState::new,
        runtime.getConfig().getAskTimeout(), context.getSystem().scheduler());
    context.pipeToSelf(call, (instance, error) ->
        new InstanceQueryCompleted(currentPollId, instance, describe(error)));
  }

  private void askInstance(Function<ActorRef<InstanceReply>, WorkflowCommand> commandFactory) {
    String currentPollId = pollId;
    CompletionStage<InstanceReply> call = AskPattern.ask(runtime.instanceActor(leaseId), commandFactory::apply,
        runtime.getConfig().getAskTimeout(), context.getSystem().scheduler());
    context.pipeToSelf(call, (reply, error) -> {
      if (error != null) {
        return new InstanceOperationCompleted(currentPollId, describe(error));
      }
      return new InstanceOperationCompleted(currentPollId, reply.accepted() ? null : reply.detail());
    });
  }

  private Effect<LeaseEvent, LeaseState> onInstanceOperationCompleted(LeaseState state, InstanceOperationCompleted command) {
    if (pollId == null || !pollId.equals(command.pollId())) {
      return Effect().none();
    }
    if (command.error() != null) {
      return recordRetryFailure(state, command.error()).thenRun(this::scheduleNextPoll);
    }
    return Effect().none().thenRun(this::scheduleNextPoll);
  }

  private Effect<LeaseEvent, LeaseState> onInstanceQueryCompleted(LeaseState state, InstanceQueryCompleted command) {
    if (pollId == null || !pollId.equals(command.pollId())) {
      return Effect().none();
    }
    if (command.error() != null) {
      return recordRetryFailure(state, command.error()).thenRun(this::scheduleNextPoll);
    }
    InstanceWorkflowState instance = command.instance();
    if (state.phase() == Phase.TERMINATING) {
      if (state.currentPeriod() != null) {
        return Effect().persist(terminationEvents(state, state.terminatedAt(), instance.failedAt(), null))
            .thenRun(this::scheduleNextPoll);
      }
      if (instance.phase() == InstancePhase.DELETED) {
        return Effect().persist(List.of(new LeaseTerminated(),
            state.settled() ? new LeaseReconciliationScheduled(null) : retryScheduled()))
            .thenRun(this::scheduleNextPoll);
      }
      if ((instance.phase() == InstancePhase.ACTIVE || instance.phase() == InstancePhase.FAILED
          || instance.phase() == InstancePhase.DELETE_FAILED)
          && !instance.hasPendingOperation()) {
        return Effect().none().thenRun(updated -> askInstance(DeleteInstance::new));
      }
      return Effect().none().thenRun(this::scheduleNextPoll);
    }
    Instant now = Instant.now();
    if (instance.phase() == InstancePhase.DELETED || instance.phase().isDeleting()
        || instance.phase() == InstancePhase.DELETE_FAILED
        || instance.phase() == InstancePhase.FAILED) {
      return Effect().persist(terminationEvents(state, now, instance.failedAt(), "INSTANCE_UNAVAILABLE"))
          .thenRun(this::scheduleNextPoll);
    }
    if (instance.phase() == InstancePhase.NEW && state.periodSeq() == 0) {
      if (!state.instanceCreationRequested()) {
        return Effect().persist(List.of(new InstanceCreationRequested(), retryScheduled())).thenRun(this::createInstance);
      }
      return Effect().none().thenRun(this::createInstance);
    }
    if (instance.phase() != InstancePhase.ACTIVE) {
      return Effect().none().thenRun(this::scheduleNextPoll);
    }
    LeasePeriod currentPeriod = state.currentPeriod();
    if (currentPeriod != null) {
      return Effect().persist(List.of(
          new SettlementRequested(new LeaseSettlement(
              currentPeriod.orderId(), currentPeriod.amount(), currentPeriod.amount())),
          new PeriodEnded(currentPeriod.endsAt()), retryScheduled()))
          .thenRun(this::scheduleNextPoll);
    }
    if (state.phase() == Phase.RENEWING) {
      return Effect().persist(List.of(
          purchaseRequested(state.renewalPlan(), state.periodSeq() + 1, state.planRevision()), retryScheduled()))
          .thenRun(this::scheduleNextPoll);
    }
    LeasePurchase purchase = state.pendingRenewal();
    Instant start = state.periodSeq() == 0 ? now : Objects.requireNonNull(state.lastPeriodEndsAt());
    LeasePeriod period = new LeasePeriod(purchase.periodSeq(), purchase.orderId(), purchase.plan(),
        purchase.amount(), start, start.plus(purchase.plan().duration()));
    return Effect().persist(List.of(new PeriodStarted(period), new LeaseReconciliationScheduled(period.endsAt())))
        .thenRun(this::scheduleNextPoll);
  }

  private void createInstance(LeaseState state) {
    askInstance(replyTo -> new CreateInstance(state.requestId(), leaseId, state.instanceSpec(), replyTo));
  }

  private Effect<LeaseEvent, LeaseState> reply(LeaseState state, ActorRef<LeaseReply> replyTo, String error) {
    return Effect().reply(replyTo, error == null ? LeaseReply.accepted(state) : LeaseReply.rejected(error, state));
  }

  private LeaseReconciliationScheduled reconciliationScheduled(LeaseState state, boolean autoRenew) {
    if (state.hasPendingWork() || state.phase() == Phase.EXPIRED && autoRenew) {
      return retryScheduled();
    }
    return new LeaseReconciliationScheduled(state.currentPeriod() == null ? null : state.currentPeriod().endsAt());
  }

  private LeaseReconciliationScheduled retryScheduled() {
    return new LeaseReconciliationScheduled(Instant.now().plus(settings.retryInterval()));
  }

  private static String describe(Throwable error) {
    return error == null ? null : error.getClass().getSimpleName();
  }

  @Override
  public EventHandler<LeaseState, LeaseEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(LeaseCreated.class, (state, event) -> state.created(event.userId(), event.requestId(),
            event.instanceSpec(), event.plan(), event.autoRenew()))
        .onEvent(RenewalPlanChanged.class, (state, event) -> state.renewalPlanChanged(event.plan(), event.planRevision()))
        .onEvent(AutoRenewConfigured.class, (state, event) -> state.autoRenewConfigured(event.enabled()))
        .onEvent(RenewalStarted.class, (state, event) -> state.renewalStarted())
        .onEvent(PurchaseRequested.class, (state, event) -> state.purchaseRequested(event.purchase()))
        .onEvent(PurchasePriced.class, (state, event) -> state.purchasePriced(event.amount()))
        .onEvent(PurchaseLocked.class, (state, event) -> state.purchaseLocked())
        .onEvent(SettlementRequested.class, (state, event) -> state.settlementRequested(event.settlement()))
        .onEvent(SettlementCompleted.class, (state, event) -> state.settlementCompleted())
        .onEvent(PeriodEnded.class, (state, event) -> state.periodEnded(event.endsAt()))
        .onEvent(PeriodStarted.class, (state, event) -> state.periodStarted(event.period()))
        .onEvent(LeaseTerminationStarted.class, (state, event) -> state.terminationStarted(event.terminatedAt()))
        .onEvent(LeaseTerminationPrepared.class,
            (state, event) -> state.terminationPrepared(event.terminatedAt(), event.reason()))
        .onEvent(LeaseTerminated.class, (state, event) -> state.terminated())
        .onEvent(InstanceCreationRequested.class, (state, event) -> state.withInstanceCreationRequested())
        .onEvent(LeaseRetryScheduled.class, (state, event) -> state.withError(event.error()))
        .onEvent(LeaseReconciliationScheduled.class, (state, event) -> state.reconciliationScheduled(event.nextActionAt()))
        .build();
  }

  @Override
  public SignalHandler<LeaseState> signalHandler() {
    return newSignalHandlerBuilder()
        .onSignal(RecoveryCompleted.class, (state, signal) -> {
          pollId = null;
          pollLeaseProgress(state);
        })
        .build();
  }

  @Override
  public RetentionCriteria retentionCriteria() {
    return RetentionCriteria.snapshotEvery(100, 2);
  }
}
