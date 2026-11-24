package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.actor.lease.command.ChangeRenewalPlan;
import cn.veryai.arcreactor.actor.lease.command.ConfigureAutoRenew;
import cn.veryai.arcreactor.actor.lease.command.RenewLease;
import cn.veryai.arcreactor.actor.lease.command.TerminateLease;
import cn.veryai.arcreactor.actor.lease.model.RentalPlan;
import cn.veryai.arcreactor.service.LeaseService;
import cn.veryai.arcreactor.vo.LeaseView;
import cn.veryai.arcreactor.vo.RespWrapper;
import cn.veryai.arcreactor.web.params.CreateLeaseParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/leases")
@RequiredArgsConstructor
public class LeaseController {
    private final LeaseService service;

    public record PlanChange(@Positive long targetPeriodSeq, @Positive long expectedPlanRevision,
            @NotNull @Valid RentalPlan plan) {}
    public record AutoRenew(boolean enabled) {}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletionStage<LeaseView.Created> create(@Valid @RequestBody CreateLeaseParam param) {
        return service.create(param);
    }

    @GetMapping("/{id}")
    public CompletionStage<LeaseView> get(@PathVariable("id") String id) { return service.get(id); }

    @GetMapping
    public List<LeaseView> list(@RequestParam("userId") String userId) { return service.list(userId); }

    @PutMapping("/{id}/renewal-plan")
    public CompletionStage<ResponseEntity<RespWrapper<LeaseView.Mutation, Object>>> plan(@PathVariable("id") String id,
            @Valid @RequestBody PlanChange param) {
        return response(service.mutate(id, reply -> new ChangeRenewalPlan(param.targetPeriodSeq(),
                param.expectedPlanRevision(), param.plan(), reply)));
    }

    @PutMapping("/{id}/auto-renew")
    public CompletionStage<ResponseEntity<RespWrapper<LeaseView.Mutation, Object>>> autoRenew(@PathVariable("id") String id,
            @RequestBody AutoRenew param) {
        return response(service.mutate(id, reply -> new ConfigureAutoRenew(param.enabled(), reply)));
    }

    @PostMapping("/{id}/renew")
    public CompletionStage<ResponseEntity<RespWrapper<LeaseView.Mutation, Object>>> renew(@PathVariable("id") String id) {
        return response(service.mutate(id, RenewLease::new));
    }

    @PostMapping("/{id}/terminate")
    public CompletionStage<ResponseEntity<RespWrapper<LeaseView.Mutation, Object>>> terminate(@PathVariable("id") String id) {
        return response(service.mutate(id, TerminateLease::new));
    }

    private CompletionStage<ResponseEntity<RespWrapper<LeaseView.Mutation, Object>>> response(CompletionStage<LeaseView.Mutation> result) {
        return result.thenApply(value -> ResponseEntity.status(value.accepted() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT)
                .body(RespWrapper.success(value)));
    }
}
