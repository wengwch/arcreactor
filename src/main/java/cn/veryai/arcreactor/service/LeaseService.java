package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.PekkoRuntime;
import cn.veryai.arcreactor.actor.lease.command.ChangeRenewalPlan;
import cn.veryai.arcreactor.actor.lease.command.ConfigureAutoRenew;
import cn.veryai.arcreactor.actor.lease.command.CreateLease;
import cn.veryai.arcreactor.actor.lease.command.GetLeaseState;
import cn.veryai.arcreactor.actor.lease.command.LeaseCommand;
import cn.veryai.arcreactor.actor.lease.command.RenewLease;
import cn.veryai.arcreactor.actor.lease.command.TerminateLease;
import cn.veryai.arcreactor.actor.lease.model.LeaseState;
import cn.veryai.arcreactor.actor.lease.reply.LeaseReply;
import cn.veryai.arcreactor.actor.order.command.GetOrderState;
import cn.veryai.arcreactor.actor.order.command.OrderCommand;
import cn.veryai.arcreactor.actor.order.model.OrderState;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.repo.RentalViewRepo;
import cn.veryai.arcreactor.vo.LeaseView;
import cn.veryai.arcreactor.web.params.ChangeRenewalPlanParam;
import cn.veryai.arcreactor.web.params.ConfigureAutoRenewParam;
import cn.veryai.arcreactor.web.params.CreateLeaseParam;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.japi.function.Function;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LeaseService {
    private final PekkoRuntime pekkoRuntime;
    private final InstanceService instanceService;
    private final RentalViewRepo rentalViewRepo;

    public CompletionStage<LeaseView.Created> create(CreateLeaseParam param) {
        String userId = param.instance().getUserId();
        String leaseId = UUID.nameUUIDFromBytes((userId.length() + ":" + userId + param.requestId())
                .getBytes(StandardCharsets.UTF_8)).toString();
        return getState(leaseId).thenCompose(existing -> {
            if (existing.phase() != LeaseState.Phase.NEW) {
                if (!userId.equals(existing.userId()) || !param.requestId().equals(existing.requestId())) {
                    throw new BaseException(ErrorCode.CONFLICT);
                }
                return CompletableFuture.completedFuture(existing);
            }
            var spec = instanceService.prepareSpec(param.instance());
            return AskPattern.<LeaseCommand, LeaseReply>ask(pekkoRuntime.leaseActor(leaseId), replyTo ->
                            new CreateLease(param.requestId(), spec, param.plan(), param.autoRenew(), replyTo),
                            pekkoRuntime.getConfig().getAskTimeout(), pekkoRuntime.getActorSystem().scheduler())
                    .thenApply(result -> {
                        if (!result.accepted()) throw new BaseException(ErrorCode.CONFLICT);
                        return result.state();
                    });
        }).thenApply(created -> {
            var credentials = created.instanceSpec().cloudInit();
            return new LeaseView.Created(LeaseView.from(created), credentials.username(), credentials.password(),
                    credentials.jupyterToken());
        });
    }

    public CompletionStage<LeaseView> get(String leaseId) {
        return getState(leaseId).thenApply(state -> {
            if (state.phase() == LeaseState.Phase.NEW) throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
            return LeaseView.from(state);
        });
    }

    public List<LeaseView> list(String userId) {
        return rentalViewRepo.findLeasesByUser(userId).stream().map(LeaseView::from).toList();
    }

    public CompletionStage<LeaseView.Mutation> changeRenewalPlan(String leaseId, ChangeRenewalPlanParam param) {
        return submitMutation(leaseId, replyTo -> new ChangeRenewalPlan(
                param.targetPeriodSeq(), param.expectedPlanRevision(), param.plan(), replyTo));
    }

    public CompletionStage<LeaseView.Mutation> configureAutoRenew(String leaseId, ConfigureAutoRenewParam param) {
        return submitMutation(leaseId, replyTo -> new ConfigureAutoRenew(param.enabled(), replyTo));
    }

    public CompletionStage<LeaseView.Mutation> renew(String leaseId) {
        return submitMutation(leaseId, RenewLease::new);
    }

    public CompletionStage<LeaseView.Mutation> terminate(String leaseId) {
        return submitMutation(leaseId, TerminateLease::new);
    }

    public CompletionStage<OrderState> getOrder(String orderId) {
        return AskPattern.<OrderCommand, OrderState>ask(pekkoRuntime.orderActor(orderId), GetOrderState::new,
                pekkoRuntime.getConfig().getAskTimeout(), pekkoRuntime.getActorSystem().scheduler())
                .thenApply(state -> {
                    if (!state.initialized()) throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
                    return state;
                });
    }

    private CompletionStage<LeaseState> getState(String leaseId) {
        return AskPattern.ask(pekkoRuntime.leaseActor(leaseId), GetLeaseState::new,
                pekkoRuntime.getConfig().getAskTimeout(), pekkoRuntime.getActorSystem().scheduler());
    }

    private CompletionStage<LeaseView.Mutation> submitMutation(String leaseId,
            Function<ActorRef<LeaseReply>, LeaseCommand> command) {
        return AskPattern.<LeaseCommand, LeaseReply>ask(pekkoRuntime.leaseActor(leaseId), command,
                pekkoRuntime.getConfig().getAskTimeout(), pekkoRuntime.getActorSystem().scheduler())
                .thenApply(reply -> new LeaseView.Mutation(reply.accepted(), reply.error(), LeaseView.from(reply.state())));
    }
}
