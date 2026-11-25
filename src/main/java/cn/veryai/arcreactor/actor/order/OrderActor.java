package cn.veryai.arcreactor.actor.order;

import cn.veryai.arcreactor.actor.order.command.CreateOrder;
import cn.veryai.arcreactor.actor.order.command.GetOrderState;
import cn.veryai.arcreactor.actor.order.command.OrderCommand;
import cn.veryai.arcreactor.actor.order.command.PollOrderProgress;
import cn.veryai.arcreactor.actor.order.command.SettleOrder;
import cn.veryai.arcreactor.actor.order.command.WalletOperationCompleted;
import cn.veryai.arcreactor.actor.order.event.OrderCreated;
import cn.veryai.arcreactor.actor.order.event.OrderDeducted;
import cn.veryai.arcreactor.actor.order.event.OrderDeductionRequested;
import cn.veryai.arcreactor.actor.order.event.OrderEvent;
import cn.veryai.arcreactor.actor.order.event.OrderInsufficientFunds;
import cn.veryai.arcreactor.actor.order.event.OrderLocked;
import cn.veryai.arcreactor.actor.order.event.OrderOperationFailed;
import cn.veryai.arcreactor.actor.order.event.UnopenedOrderSettled;
import cn.veryai.arcreactor.actor.order.model.OrderState;
import cn.veryai.arcreactor.actor.order.model.OrderState.Phase;
import cn.veryai.arcreactor.actor.order.model.WalletAction;
import cn.veryai.arcreactor.service.FundWalletService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.ActorRef;
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

/** Freezes one period's funds and settles its fixed consumption amount once. */
public final class OrderActor extends EventSourcedBehavior<OrderCommand, OrderEvent, OrderState> {
  public static final String ENTITY_TYPE = "order";
  public static final EntityTypeKey<OrderCommand> TYPE_KEY = EntityTypeKey.create(OrderCommand.class, ENTITY_TYPE);

  private final String orderId;
  private final ActorContext<OrderCommand> context;
  private final TimerScheduler<OrderCommand> timers;
  private final FundWalletService walletService;
  private final Executor blockingExecutor;
  private final OrderSettings settings;
  private String operationToken;

  public static Behavior<OrderCommand> create(String orderId, FundWalletService walletService,
      Executor blockingExecutor, OrderSettings settings) {
    return Behaviors.setup(context -> Behaviors.withTimers(
        timers -> new OrderActor(orderId, context, timers, walletService, blockingExecutor, settings)));
  }

  private OrderActor(String orderId, ActorContext<OrderCommand> context, TimerScheduler<OrderCommand> timers,
      FundWalletService walletService, Executor blockingExecutor, OrderSettings settings) {
    super(PersistenceId.of(ENTITY_TYPE, orderId));
    this.orderId = orderId;
    this.context = context;
    this.timers = timers;
    this.walletService = walletService;
    this.blockingExecutor = blockingExecutor;
    this.settings = settings;
  }

  @Override
  public OrderState emptyState() {
    return OrderState.empty(orderId);
  }

  @Override
  public CommandHandler<OrderCommand, OrderEvent, OrderState> commandHandler() {
    return newCommandHandlerBuilder().forAnyState()
        .onCommand(CreateOrder.class, this::onCreate)
        .onCommand(SettleOrder.class, this::onSettle)
        .onCommand(GetOrderState.class, this::onGetState)
        .onCommand(PollOrderProgress.class, this::onPollProgress)
        .onCommand(WalletOperationCompleted.class, this::onWalletCompleted)
        .build();
  }

  private Effect<OrderEvent, OrderState> onCreate(OrderState state, CreateOrder command) {
    if (!hasText(command.userId()) || !hasText(command.leaseId()) || !hasText(command.intentId())
        || command.amount() == null || command.amount().signum() <= 0) {
      return reject(state, command.replyTo(), "User, lease, intent and a positive amount are required");
    }
    BigDecimal orderAmount = command.amount().setScale(2, RoundingMode.HALF_UP);
    if (orderAmount.signum() <= 0) {
      return reject(state, command.replyTo(), "Order amount must be at least one cent");
    }
    if (state.initialized()) {
      // Concurrent retries may quote again; the first persisted order keeps its price.
      return matches(state, command.userId(), command.leaseId(), command.intentId())
          ? Effect().reply(command.replyTo(), state) : reject(state, command.replyTo(), "Order binding differs");
    }
    return Effect().persist(new OrderCreated(command.userId(), command.leaseId(), command.intentId(),
        orderAmount))
        .thenRun(updated -> {
          command.replyTo().tell(updated);
          retryWalletOperation(updated);
        });
  }

  private Effect<OrderEvent, OrderState> onSettle(OrderState state, SettleOrder command) {
    if (!hasText(command.userId()) || !hasText(command.leaseId()) || !hasText(command.intentId())
        || command.amount() == null || command.deductAmount() == null
        || command.amount().signum() < 0 || command.deductAmount().signum() < 0) {
      return reject(state, command.replyTo(), "Invalid order settlement");
    }
    BigDecimal orderAmount = command.amount().setScale(2, RoundingMode.HALF_UP);
    BigDecimal deductAmount = command.deductAmount().setScale(2, RoundingMode.HALF_UP);
    if ((command.amount().signum() > 0 && orderAmount.signum() == 0) || deductAmount.compareTo(orderAmount) > 0) {
      return reject(state, command.replyTo(), "Invalid order settlement");
    }
    if (!state.initialized()) {
      if (orderAmount.signum() > 0) {
        return reject(state, command.replyTo(), "Order has not been created");
      }
      // A termination arriving before create must prevent a delayed create from freezing funds.
      return Effect().persist(new UnopenedOrderSettled(command.userId(), command.leaseId(), command.intentId()))
          .thenRun(updated -> command.replyTo().tell(updated));
    }
    if (!matches(state, command.userId(), command.leaseId(), command.intentId())
        || orderAmount.signum() > 0 && state.amount().compareTo(orderAmount) != 0) {
      return reject(state, command.replyTo(), "Order binding differs");
    }
    if (orderAmount.signum() == 0) {
      // Return the persisted price so the lease can calculate consumption at its saved cutoff.
      return Effect().reply(command.replyTo(), state);
    }
    if (state.deductAmount() != null) {
      return state.deductAmount().compareTo(deductAmount) == 0 ? Effect().reply(command.replyTo(), state)
          : reject(state, command.replyTo(), "Settlement amount differs from the original request");
    }
    if (state.phase() == Phase.INSUFFICIENT_FUNDS) {
      return Effect().reply(command.replyTo(), state);
    }
    return Effect().persist(new OrderDeductionRequested(deductAmount))
        .thenRun(updated -> {
          command.replyTo().tell(updated);
          retryWalletOperation(updated);
        });
  }

  private static boolean matches(OrderState state, String userId, String leaseId, String intentId) {
    return Objects.equals(state.userId(), userId) && Objects.equals(state.leaseId(), leaseId)
        && Objects.equals(state.intentId(), intentId);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private Effect<OrderEvent, OrderState> onGetState(OrderState state, GetOrderState command) {
    return Effect().reply(command.replyTo(), state);
  }

  private Effect<OrderEvent, OrderState> onPollProgress(OrderState state, PollOrderProgress command) {
    return Effect().none().thenRun(this::retryWalletOperation);
  }

  private void retryWalletOperation(OrderState state) {
    if (!state.hasPendingWork()) {
      timers.cancel(PollOrderProgress.class);
      return;
    }
    if (!timers.isTimerActive(PollOrderProgress.class)) {
      timers.startTimerWithFixedDelay(PollOrderProgress.class, new PollOrderProgress(), settings.retryInterval());
    }
    if (operationToken != null) {
      return;
    }
    String token = UUID.randomUUID().toString();
    operationToken = token;
    WalletAction action = state.phase() == Phase.LOCKING ? WalletAction.LOCK : WalletAction.DEDUCT;
    // The wallet APIs are synchronous. Retries reuse the order ID and persisted amounts.
    var operation = CompletableFuture.supplyAsync(() -> action == WalletAction.LOCK
        ? walletService.lock(state.userId(), state.amount(), orderId, "GPU_RENTAL", null)
        : walletService.deduct(state.userId(), orderId, state.deductAmount(), null),
        blockingExecutor).orTimeout(settings.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
    context.pipeToSelf(operation, (response, error) -> new WalletOperationCompleted(token, action,
        response == null ? null : response.isSuccess(),
        error != null ? error.toString() : response == null ? "Fund service returned no result" : response.getMessage()));
  }

  private Effect<OrderEvent, OrderState> onWalletCompleted(OrderState state, WalletOperationCompleted command) {
    if (!Objects.equals(operationToken, command.token())) {
      return Effect().none();
    }
    operationToken = null;
    if (command.success() == null) {
      if (Objects.equals(state.error(), command.error())) {
        return Effect().none();
      }
      return Effect().persist(new OrderOperationFailed(command.error()));
    }
    if (!command.success()) {
      return Effect().persist(new OrderInsufficientFunds()).thenRun(this::retryWalletOperation);
    }
    return Effect().persist(command.action() == WalletAction.LOCK ? new OrderLocked() : new OrderDeducted())
        .thenRun(this::retryWalletOperation);
  }

  private Effect<OrderEvent, OrderState> reject(OrderState state, ActorRef<OrderState> replyTo, String reason) {
    return Effect().reply(replyTo, state.withError(reason));
  }

  @Override
  public EventHandler<OrderState, OrderEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(OrderCreated.class,
            (state, event) -> state.created(event.userId(), event.leaseId(), event.intentId(), event.amount()))
        .onEvent(UnopenedOrderSettled.class,
            (state, event) -> state.settledWithoutLock(event.userId(), event.leaseId(), event.intentId()))
        .onEvent(OrderLocked.class, (state, event) -> state.locked())
        .onEvent(OrderDeductionRequested.class, (state, event) -> state.deductionRequested(event.amount()))
        .onEvent(OrderDeducted.class, (state, event) -> state.deducted())
        .onEvent(OrderInsufficientFunds.class, (state, event) -> state.insufficientFunds())
        .onEvent(OrderOperationFailed.class, (state, event) -> state.withError(event.error()))
        .build();
  }

  @Override
  public SignalHandler<OrderState> signalHandler() {
    return newSignalHandlerBuilder()
        .onSignal(RecoveryCompleted.class, (state, signal) -> {
          operationToken = null;
          retryWalletOperation(state);
        })
        .build();
  }

  @Override
  public RetentionCriteria retentionCriteria() {
    return RetentionCriteria.snapshotEvery(100, 2);
  }
}
