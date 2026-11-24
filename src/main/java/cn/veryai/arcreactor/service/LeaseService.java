package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.PekkoRuntime;
import cn.veryai.arcreactor.actor.lease.command.CreateLease;
import cn.veryai.arcreactor.actor.lease.command.GetLeaseState;
import cn.veryai.arcreactor.actor.lease.command.LeaseCommand;
import cn.veryai.arcreactor.actor.lease.model.LeaseState;
import cn.veryai.arcreactor.actor.lease.reply.LeaseReply;
import cn.veryai.arcreactor.actor.order.command.GetOrderState;
import cn.veryai.arcreactor.actor.order.command.OrderCommand;
import cn.veryai.arcreactor.actor.order.model.OrderState;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.repo.RentalViewRepo;
import cn.veryai.arcreactor.util.JsonUtil;
import cn.veryai.arcreactor.vo.LeaseView;
import cn.veryai.arcreactor.web.params.CreateLeaseParam;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LeaseService {
    private final PekkoRuntime runtime;
    private final InstanceService instances;
    private final RentalViewRepo views;

    public CompletionStage<LeaseView.Created> create(CreateLeaseParam param) {
        String userId = param.instance().getUserId();
        String id = UUID.nameUUIDFromBytes((userId.length() + ":" + userId + param.requestId())
                .getBytes(StandardCharsets.UTF_8)).toString();
        String fingerprint = JsonUtil.toJson(param);
        return state(id).thenCompose(existing -> {
            if (existing.phase() != LeaseState.Phase.NEW) {
                if (!fingerprint.equals(existing.fingerprint())) throw new BaseException(ErrorCode.CONFLICT);
                return CompletableFuture.completedFuture(existing);
            }
            var spec = instances.prepareSpec(param.instance());
            return AskPattern.<LeaseCommand, LeaseReply>ask(runtime.leaseActor(id), reply ->
                            new CreateLease(param.requestId(), fingerprint, param.instance().getFlavorId(),
                                    spec, param.plan(), param.autoRenew(), reply),
                            runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler())
                    .thenApply(result -> {
                        if (!result.accepted()) throw new BaseException(ErrorCode.CONFLICT);
                        return result.state();
                    });
        })
        .thenApply(created -> {
            var credentials = created.instanceSpec().cloudInit();
            return new LeaseView.Created(LeaseView.from(created), credentials.username(), credentials.password(), credentials.jupyterToken());
        });
    }

    public CompletionStage<LeaseView> get(String id) {
        return state(id).thenApply(value -> {
            if (value.phase() == LeaseState.Phase.NEW) throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
            return LeaseView.from(value);
        });
    }

    public List<LeaseView> list(String userId) {
        return views.findLeasesByUser(userId).stream().map(LeaseView::from).toList();
    }

    public CompletionStage<LeaseView.Mutation> mutate(String id, Function<ActorRef<LeaseReply>, LeaseCommand> command) {
        return AskPattern.<LeaseCommand, LeaseReply>ask(runtime.leaseActor(id), command::apply,
                runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler())
                .thenApply(reply -> new LeaseView.Mutation(reply.accepted(), reply.error(), LeaseView.from(reply.state())));
    }

    public CompletionStage<OrderState> order(String id) {
        return AskPattern.<OrderCommand, OrderState>ask(runtime.orderActor(id), GetOrderState::new,
                runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler()).thenApply(state -> {
                    if (!state.initialized()) throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
                    return state;
                });
    }

    private CompletionStage<LeaseState> state(String id) {
        return AskPattern.ask(runtime.leaseActor(id), GetLeaseState::new,
                runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler());
    }
}
