package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.service.LeaseService;
import cn.veryai.arcreactor.vo.LeaseView;
import cn.veryai.arcreactor.vo.RespWrapper;
import cn.veryai.arcreactor.web.params.ChangeRenewalPlanParam;
import cn.veryai.arcreactor.web.params.ConfigureAutoRenewParam;
import cn.veryai.arcreactor.web.params.CreateLeaseParam;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leases")
@RequiredArgsConstructor
public class LeaseController {
    private final LeaseService leaseService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletionStage<LeaseView.Created> createLease(@Valid @RequestBody CreateLeaseParam param) {
        return leaseService.create(param);
    }

    @GetMapping("/{leaseId}")
    public CompletionStage<LeaseView> getLease(@PathVariable("leaseId") String leaseId) {
        return leaseService.get(leaseId);
    }

    @GetMapping
    public List<LeaseView> listLeases(@RequestParam("userId") String userId) {
        return leaseService.list(userId);
    }

    @PutMapping("/{leaseId}/renewal-plan")
    public CompletionStage<ResponseEntity<RespWrapper<LeaseView.Mutation, Object>>> changeRenewalPlan(
            @PathVariable("leaseId") String leaseId, @Valid @RequestBody ChangeRenewalPlanParam param) {
        return mutationResponse(leaseService.changeRenewalPlan(leaseId, param));
    }

    @PutMapping("/{leaseId}/auto-renew")
    public CompletionStage<ResponseEntity<RespWrapper<LeaseView.Mutation, Object>>> configureAutoRenew(
            @PathVariable("leaseId") String leaseId, @Valid @RequestBody ConfigureAutoRenewParam param) {
        return mutationResponse(leaseService.configureAutoRenew(leaseId, param));
    }

    @PostMapping("/{leaseId}/renew")
    public CompletionStage<ResponseEntity<RespWrapper<LeaseView.Mutation, Object>>> renewLease(
            @PathVariable("leaseId") String leaseId) {
        return mutationResponse(leaseService.renew(leaseId));
    }

    @PostMapping("/{leaseId}/terminate")
    public CompletionStage<ResponseEntity<RespWrapper<LeaseView.Mutation, Object>>> terminateLease(
            @PathVariable("leaseId") String leaseId) {
        return mutationResponse(leaseService.terminate(leaseId));
    }

    private CompletionStage<ResponseEntity<RespWrapper<LeaseView.Mutation, Object>>> mutationResponse(
            CompletionStage<LeaseView.Mutation> mutation) {
        return mutation.thenApply(result -> ResponseEntity.status(result.accepted() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT)
                .body(RespWrapper.success(result)));
    }
}
