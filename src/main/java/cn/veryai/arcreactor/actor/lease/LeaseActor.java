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
import cn.veryai.arcreactor.actor.lease.command.LeaseCommand;
import cn.veryai.arcreactor.actor.lease.command.LeaseOperationCompleted;
import cn.veryai.arcreactor.actor.lease.command.PollLeaseProgress;
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
import cn.veryai.arcreactor.actor.lease.event.LeaseTerminationRequested;
import cn.veryai.arcreactor.actor.lease.event.PeriodEnded;
import cn.veryai.arcreactor.actor.lease.event.PeriodStarted;
import cn.veryai.arcreactor.actor.lease.event.PurchasePhaseChanged;
import cn.veryai.arcreactor.actor.lease.event.PurchaseRequested;
import cn.veryai.arcreactor.actor.lease.event.RenewalPlanChanged;
import cn.veryai.arcreactor.actor.lease.event.SettlementCompleted;
import cn.veryai.arcreactor.actor.lease.event.SettlementRequested;
import cn.veryai.arcreactor.actor.lease.model.LeasePeriod;
import cn.veryai.arcreactor.actor.lease.model.LeasePurchase;
import cn.veryai.arcreactor.actor.lease.model.LeaseSettlement;
import cn.veryai.arcreactor.actor.lease.model.LeaseState;
import cn.veryai.arcreactor.actor.lease.model.LeaseState.Phase;
import cn.veryai.arcreactor.actor.lease.model.LeaseState.PurchasePhase;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final Map<String, String> operationTokens = new HashMap<>();

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
        .onCommand(LeaseOperationCompleted.class, this::onOperationCompleted)
        .build();
  }

  private Effect<LeaseEvent, LeaseState> onCreate(LeaseState state, CreateLease command) {
    if (state.phase() != Phase.NEW) {
      return reply(state, command.replyTo(),
          Objects.equals(state.fingerprint(), command.fingerprint()) ? null : "REQUEST_CONFLICT");
    }
    LeaseCreated created = new LeaseCreated(command.spec().share().userId(), command.requestId(), command.fingerprint(),
        command.spec(), command.plan(), command.autoRenew());
    return Effect().persist(List.of(created, purchaseRequested(command.plan(), 1, 1, false), retryScheduled()))
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
    if (state.pendingRenewal() != null) {
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
    if (state.currentPeriod() != null && Instant.now().isBefore(state.currentPeriod().endsAt())) {
      return reply(state, command.replyTo(), "CURRENT_PERIOD_NOT_ENDED");
    }
    if (state.pendingRenewal() != null) {
      return reply(state, command.replyTo(), null);
    }
    List<LeaseEvent> events = new ArrayList<>();
    if (state.currentPeriod() != null) {
      events.add(new SettlementRequested(settlement(state.currentPeriod(), state.currentPeriod().amount())));
      events.add(new PeriodEnded(state.currentPeriod().endsAt()));
    }
    events.add(purchaseRequested(state.renewalPlan(), state.periodSeq() + 1, state.planRevision(),
        state.currentPeriod() != null || state.pendingSettlement() != null));
    events.add(retryScheduled());
    return Effect().persist(events).thenRun(updated -> {
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
    List<LeaseEvent> events = new ArrayList<>();
    addTerminationEvents(state, events, Instant.now(), null);
    events.add(retryScheduled());
    return Effect().persist(events).thenRun(updated -> {
      command.replyTo().tell(LeaseReply.accepted(updated));
      pollLeaseProgress(updated);
    });
  }

  private PurchaseRequested purchaseRequested(RentalPlan plan, long periodSeq, long planRevision,
      boolean waitingForSettlement) {
    String orderId = UUID.randomUUID().toString();
    String intentId = leaseId + ":" + periodSeq + ":" + orderId;
    return new PurchaseRequested(new LeasePurchase(orderId, intentId, periodSeq, plan,
        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), planRevision,
        waitingForSettlement ? PurchasePhase.WAITING_SETTLEMENT : PurchasePhase.CREATING_ORDER));
  }

  private LeaseSettlement settlement(LeasePeriod period, BigDecimal deductAmount) {
    return new LeaseSettlement(period.orderId(), period.intentId(), period.amount(), deductAmount);
  }

  private LeaseSettlement purchaseSettlement(LeaseState state, String orderId, String intentId,
      BigDecimal amount, Instant cutoff) {
    BigDecimal deductAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    if (amount.signum() > 0 && state.periodSeq() > 0) {
      Instant start = Objects.requireNonNull(state.lastPeriodEndsAt());
      RentalPlan plan = state.renewalPlan();
      deductAmount = new LeasePeriod(state.periodSeq() + 1, orderId, intentId,
          plan, amount, start, start.plus(plan.duration())).deductAt(cutoff);
    }
    return new LeaseSettlement(orderId, intentId, amount, deductAmount);
  }

  private void addTerminationEvents(LeaseState state, List<LeaseEvent> events, Instant cutoff, String reason) {
    if (state.currentPeriod() != null) {
      events.add(new SettlementRequested(settlement(state.currentPeriod(), state.currentPeriod().deductAt(cutoff))));
    } else if (state.pendingSettlement() == null && state.pendingRenewal() != null) {
      LeasePurchase purchase = state.pendingRenewal();
      if (purchase.phase() != PurchasePhase.WAITING_SETTLEMENT) {
        // Creation may have reached the order even when its reply and amount are still unknown.
        events.add(new SettlementRequested(purchaseSettlement(
            state, purchase.orderId(), purchase.intentId(), purchase.amount(), cutoff)));
      }
    }
    events.add(new LeaseTerminationRequested(cutoff, reason));
    if (!state.instanceCreationRequested()) {
      events.add(new LeaseTerminated());
    }
  }

  private Effect<LeaseEvent, LeaseState> onReconcile(LeaseState state, ReconcileLease command) {
    Instant now = Instant.now();
    if (state.phase() == Phase.NEW || state.nextActionAt() == null || now.isBefore(state.nextActionAt())) {
      return Effect().none();
    }
    if (state.currentPeriod() != null && !now.isBefore(state.currentPeriod().endsAt())) {
      List<LeaseEvent> events = new ArrayList<>();
      events.add(new SettlementRequested(settlement(state.currentPeriod(), state.currentPeriod().amount())));
      events.add(new PeriodEnded(state.currentPeriod().endsAt()));
      if (state.autoRenew()) {
        events.add(purchaseRequested(state.renewalPlan(), state.periodSeq() + 1, state.planRevision(), true));
      }
      events.add(retryScheduled());
      return Effect().persist(events).thenRun(this::pollLeaseProgress);
    }
    if (state.phase() == Phase.EXPIRED && state.pendingRenewal() == null && state.autoRenew()) {
      return Effect().persist(List.of(purchaseRequested(state.renewalPlan(), state.periodSeq() + 1,
          state.planRevision(), state.pendingSettlement() != null), retryScheduled()))
          .thenRun(this::pollLeaseProgress);
    }
    return Effect().persist(reconciliationScheduled(state, state.autoRenew())).thenRun(this::pollLeaseProgress);
  }

  private Effect<LeaseEvent, LeaseState> onPollProgress(LeaseState state, PollLeaseProgress command) {
    return Effect().none().thenRun(this::pollLeaseProgress);
  }

  private void pollLeaseProgress(LeaseState state) {
    boolean instancePending = !state.resourceDeleted()
        && (state.phase() == Phase.PROVISIONING || state.terminating() && state.instanceCreationRequested());
    if (state.pendingRenewal() == null && state.pendingSettlement() == null && !instancePending) {
      timers.cancel(PollLeaseProgress.class);
      return;
    }
    if (!timers.isTimerActive(PollLeaseProgress.class)) {
      timers.startTimerWithFixedDelay(PollLeaseProgress.class, new PollLeaseProgress(), settings.retryInterval());
    }
    if (state.pendingSettlement() != null) {
      pollPendingSettlement(state, state.pendingSettlement());
    } else if (state.pendingRenewal() != null) {
      pollPendingRenewal(state, state.pendingRenewal());
    }
    if (instancePending) {
      queryInstance();
    }
  }

  private void pollPendingSettlement(LeaseState state, LeaseSettlement pendingSettlement) {
    askOrder(pendingSettlement.orderId(), replyTo ->
        new SettleOrder(state.userId(), leaseId, pendingSettlement.intentId(), pendingSettlement.amount(),
            pendingSettlement.deductAmount(), replyTo));
  }

  private void pollPendingRenewal(LeaseState state, LeasePurchase pendingRenewal) {
    switch (pendingRenewal.phase()) {
      case CREATING_ORDER -> createOrder(state, pendingRenewal);
      case LOCKING -> askOrder(pendingRenewal.orderId(), GetOrderState::new);
      case WAITING_SETTLEMENT, WAITING_ACTIVATION -> { }
    }
  }

  private Effect<LeaseEvent, LeaseState> observeOrder(LeaseState state, OrderState order) {
    LeaseSettlement pendingSettlement = state.pendingSettlement();
    if (pendingSettlement != null && pendingSettlement.orderId().equals(order.orderId())) {
      return observeSettlementOrder(state, pendingSettlement, order);
    }
    LeasePurchase pendingRenewal = state.pendingRenewal();
    if (pendingRenewal != null && pendingRenewal.orderId().equals(order.orderId())) {
      return observeRenewalOrder(state, pendingRenewal, order);
    }
    return Effect().none();
  }

  private Effect<LeaseEvent, LeaseState> observeSettlementOrder(LeaseState state, LeaseSettlement pendingSettlement,
      OrderState order) {
    if (!order.initialized()) {
      return recordRetryFailure(state, "ORDER_NOT_FOUND");
    }
    if (!matches(state, order, pendingSettlement.intentId(), pendingSettlement.amount())) {
      return recordRetryFailure(state, "ORDER_BINDING_CONFLICT");
    }
    if (order.phase() == OrderState.Phase.INSUFFICIENT_FUNDS) {
      List<LeaseEvent> events = new ArrayList<>();
      events.add(new SettlementCompleted(order.orderId()));
      if (!state.terminating()) {
        addTerminationEvents(state, events, Instant.now(), "INSUFFICIENT_FUNDS");
      } else {
        events.add(new LeaseRetryScheduled("INSUFFICIENT_FUNDS"));
      }
      events.add(retryScheduled());
      return Effect().persist(events).thenRun(this::pollLeaseProgress);
    }
    if (pendingSettlement.amount().signum() == 0 && order.amount().signum() > 0) {
      return Effect().persist(new SettlementRequested(purchaseSettlement(state, order.orderId(), order.intentId(),
          order.amount(), state.terminatedAt()))).thenRun(this::pollLeaseProgress);
    }
    if (order.deductAmount() != null && order.deductAmount().compareTo(pendingSettlement.deductAmount()) != 0) {
      return recordRetryFailure(state, "ORDER_SETTLEMENT_CONFLICT");
    }
    if (order.phase() == OrderState.Phase.SETTLED) {
      List<LeaseEvent> events = new ArrayList<>();
      events.add(new SettlementCompleted(order.orderId()));
      if (state.pendingRenewal() != null && state.pendingRenewal().phase() == PurchasePhase.WAITING_SETTLEMENT) {
        events.add(new PurchasePhaseChanged(state.pendingRenewal().orderId(), PurchasePhase.CREATING_ORDER));
      }
      events.add(retryScheduled());
      return Effect().persist(events).thenRun(this::pollLeaseProgress);
    }
    return Effect().none();
  }

  private Effect<LeaseEvent, LeaseState> observeRenewalOrder(LeaseState state, LeasePurchase pendingRenewal,
      OrderState order) {
    if (!order.initialized()) {
      return recordRetryFailure(state, "ORDER_NOT_FOUND");
    }
    if (!matches(state, order, pendingRenewal.intentId(), pendingRenewal.amount())) {
      return recordRetryFailure(state, "ORDER_BINDING_CONFLICT");
    }
    if (order.phase() == OrderState.Phase.INSUFFICIENT_FUNDS) {
      List<LeaseEvent> events = new ArrayList<>();
      addTerminationEvents(state, events, Instant.now(), "INSUFFICIENT_FUNDS");
      events.add(retryScheduled());
      return Effect().persist(events).thenRun(this::pollLeaseProgress);
    }
    if (order.deductAmount() != null) {
      return recordRetryFailure(state, "ORDER_ALREADY_SETTLING");
    }
    if (order.phase() == OrderState.Phase.LOCKED && pendingRenewal.phase() != PurchasePhase.WAITING_ACTIVATION) {
      return Effect().persist(List.of(
          new PurchasePhaseChanged(pendingRenewal.orderId(), PurchasePhase.WAITING_ACTIVATION, order.amount()), retryScheduled()))
          .thenRun(this::pollLeaseProgress);
    }
    if (order.phase() == OrderState.Phase.LOCKING && pendingRenewal.phase() == PurchasePhase.CREATING_ORDER) {
      return Effect().persist(new PurchasePhaseChanged(pendingRenewal.orderId(), PurchasePhase.LOCKING, order.amount()))
          .thenRun(this::pollLeaseProgress);
    }
    return Effect().none();
  }

  private boolean matches(LeaseState state, OrderState order, String intentId, BigDecimal amount) {
    return leaseId.equals(order.leaseId()) && Objects.equals(state.userId(), order.userId())
        && intentId.equals(order.intentId()) && (amount.signum() == 0 || amount.compareTo(order.amount()) == 0);
  }

  private Effect<LeaseEvent, LeaseState> recordRetryFailure(LeaseState state, String error) {
    return Objects.equals(state.error(), error) ? Effect().none() : Effect().persist(new LeaseRetryScheduled(error));
  }

  private void createOrder(LeaseState state, LeasePurchase purchase) {
    String key = "order:" + purchase.orderId();
    if (operationTokens.containsKey(key)) {
      return;
    }
    String token = UUID.randomUUID().toString();
    operationTokens.put(key, token);
    var orderActor = runtime.orderActor(purchase.orderId());
    var timeout = runtime.getConfig().getAskTimeout();
    var scheduler = context.getSystem().scheduler();
    CompletionStage<OrderState> call = AskPattern.<OrderCommand, OrderState>ask(
        orderActor, GetOrderState::new, timeout, scheduler).thenCompose(order -> {
          if (order.initialized()) {
            return CompletableFuture.completedFuture(order);
          }
          // OrderCreated fixes the price; a lost create reply must not trigger a new quote.
          return CompletableFuture.supplyAsync(
              () -> priceService.quote(state.instanceSpec().hypervisorType(), purchase.plan()),
              runtime.getBlockingExecutor()).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
              .thenCompose(amount -> AskPattern.<OrderCommand, OrderState>ask(orderActor,
                  replyTo -> new CreateOrder(state.userId(), leaseId, purchase.intentId(), amount, replyTo),
                  timeout, scheduler));
        });
    context.pipeToSelf(call, (order, error) ->
        new LeaseOperationCompleted(key, token, order, null, describe(error)));
  }

  private void askOrder(String orderId, Function<ActorRef<OrderState>, OrderCommand> commandFactory) {
    String key = "order:" + orderId;
    if (operationTokens.containsKey(key)) {
      return;
    }
    String token = UUID.randomUUID().toString();
    operationTokens.put(key, token);
    CompletionStage<OrderState> call = AskPattern.ask(runtime.orderActor(orderId), commandFactory::apply,
        runtime.getConfig().getAskTimeout(), context.getSystem().scheduler());
    context.pipeToSelf(call, (order, error) ->
        new LeaseOperationCompleted(key, token, order, null, describe(error)));
  }

  private void queryInstance() {
    String key = "instance-query";
    if (operationTokens.containsKey(key)) {
      return;
    }
    String token = UUID.randomUUID().toString();
    operationTokens.put(key, token);
    CompletionStage<InstanceWorkflowState> call = AskPattern.ask(runtime.instanceActor(leaseId), GetInstanceState::new,
        runtime.getConfig().getAskTimeout(), context.getSystem().scheduler());
    context.pipeToSelf(call, (instance, error) ->
        new LeaseOperationCompleted(key, token, null, instance, describe(error)));
  }

  private void askInstance(Function<ActorRef<InstanceReply>, WorkflowCommand> commandFactory) {
    String key = "instance-action";
    if (operationTokens.containsKey(key)) {
      return;
    }
    String token = UUID.randomUUID().toString();
    operationTokens.put(key, token);
    CompletionStage<InstanceReply> call = AskPattern.ask(runtime.instanceActor(leaseId), commandFactory::apply,
        runtime.getConfig().getAskTimeout(), context.getSystem().scheduler());
    context.pipeToSelf(call, (reply, error) ->
        new LeaseOperationCompleted(key, token, null, null, describe(error)));
  }

  private Effect<LeaseEvent, LeaseState> onOperationCompleted(LeaseState state, LeaseOperationCompleted command) {
    if (!operationTokens.remove(command.key(), command.token())) {
      return Effect().none();
    }
    if (command.error() != null) {
      return recordRetryFailure(state, command.error());
    }
    if (command.order() != null) {
      return observeOrder(state, command.order());
    }
    if (command.instance() != null) {
      return observeInstance(state, command.instance());
    }
    return Effect().none();
  }

  private Effect<LeaseEvent, LeaseState> observeInstance(LeaseState state, InstanceWorkflowState instance) {
    if (state.terminating()) {
      if (instance.phase() == InstancePhase.DELETED) {
        return state.resourceDeleted() ? Effect().none()
            : Effect().persist(List.of(new LeaseTerminated(),
                state.settled() ? new LeaseReconciliationScheduled(null) : retryScheduled()))
                .thenRun(this::pollLeaseProgress);
      }
      if (instance.phase() == InstancePhase.NEW && state.instanceCreationRequested()) {
        // Resolve a lost create reply before deleting through the existing instance workflow.
        createInstance(state);
      } else if ((instance.phase() == InstancePhase.ACTIVE || instance.phase() == InstancePhase.FAILED)
          && !instance.hasPendingOperation()) {
        askInstance(DeleteInstance::new);
      }
      return Effect().none();
    }
    if (instance.phase() == InstancePhase.DELETED || instance.phase().isDeleting()
        || instance.phase() == InstancePhase.FAILED && state.periodSeq() == 0) {
      List<LeaseEvent> events = new ArrayList<>();
      addTerminationEvents(state, events, Instant.now(), "INSTANCE_UNAVAILABLE");
      events.add(retryScheduled());
      return Effect().persist(events).thenRun(this::pollLeaseProgress);
    }
    LeasePurchase purchase = state.pendingRenewal();
    if (purchase == null || purchase.phase() != PurchasePhase.WAITING_ACTIVATION) {
      return Effect().none();
    }
    if (instance.phase() == InstancePhase.NEW) {
      if (!state.instanceCreationRequested()) {
        return Effect().persist(List.of(new InstanceCreationRequested(), retryScheduled())).thenRun(this::createInstance);
      }
      createInstance(state);
    } else if (instance.phase() == InstancePhase.ACTIVE) {
      Instant start = state.periodSeq() == 0 ? Instant.now() : Objects.requireNonNull(state.lastPeriodEndsAt());
      LeasePeriod period = new LeasePeriod(purchase.periodSeq(), purchase.orderId(), purchase.intentId(), purchase.plan(),
          purchase.amount(), start, start.plus(purchase.plan().duration()));
      return Effect().persist(List.of(new PeriodStarted(period), new LeaseReconciliationScheduled(period.endsAt())))
          .thenRun(this::pollLeaseProgress);
    }
    return Effect().none();
  }

  private void createInstance(LeaseState state) {
    askInstance(replyTo -> new CreateInstance(state.requestId(), leaseId, state.instanceSpec(), replyTo));
  }

  private Effect<LeaseEvent, LeaseState> reply(LeaseState state, ActorRef<LeaseReply> replyTo, String error) {
    return Effect().reply(replyTo, error == null ? LeaseReply.accepted(state) : LeaseReply.rejected(error, state));
  }

  private LeaseReconciliationScheduled reconciliationScheduled(LeaseState state, boolean autoRenew) {
    Instant due;
    if (state.phase() == Phase.NEW || state.resourceDeleted() && state.settled()) {
      due = null;
    } else if (state.currentPeriod() != null) {
      due = state.currentPeriod().endsAt();
    } else if (state.phase() == Phase.EXPIRED && state.pendingRenewal() == null
        && state.pendingSettlement() == null && !autoRenew) {
      due = null;
    } else {
      due = Instant.now().plus(settings.retryInterval());
    }
    return new LeaseReconciliationScheduled(due);
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
            event.fingerprint(), event.instanceSpec(), event.plan(), event.autoRenew()))
        .onEvent(RenewalPlanChanged.class, (state, event) -> state.renewalPlanChanged(event.plan(), event.planRevision()))
        .onEvent(AutoRenewConfigured.class, (state, event) -> state.autoRenewConfigured(event.enabled()))
        .onEvent(PurchaseRequested.class, (state, event) -> state.purchaseRequested(event.purchase()))
        .onEvent(PurchasePhaseChanged.class,
            (state, event) -> state.purchasePhaseChanged(event.phase(), event.orderAmount()))
        .onEvent(SettlementRequested.class, (state, event) -> state.settlementRequested(event.settlement()))
        .onEvent(SettlementCompleted.class, (state, event) -> state.settlementCompleted())
        .onEvent(PeriodEnded.class, (state, event) -> state.periodEnded(event.endsAt()))
        .onEvent(PeriodStarted.class, (state, event) -> state.periodStarted(event.period()))
        .onEvent(LeaseTerminationRequested.class,
            (state, event) -> state.terminationRequested(event.terminatedAt(), event.reason()))
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
          operationTokens.clear();
          pollLeaseProgress(state);
        })
        .build();
  }

  @Override
  public RetentionCriteria retentionCriteria() {
    return RetentionCriteria.snapshotEvery(100, 2);
  }
}
