package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.InstanceCurrentQosEntity;
import cn.veryai.arcreactor.entity.InstanceQosRecordEntity;
import cn.veryai.arcreactor.entity.PortForwardingEntity;
import cn.veryai.arcreactor.service.InstanceService;
import cn.veryai.arcreactor.vo.CreateInstanceResult;
import cn.veryai.arcreactor.vo.InstanceView;
import cn.veryai.arcreactor.vo.InstanceMutationResult;
import cn.veryai.arcreactor.vo.RespWrapper;
import cn.veryai.arcreactor.web.params.CreateInstanceParam;
import cn.veryai.arcreactor.web.params.CreateUserForwardingParam;
import cn.veryai.arcreactor.web.params.UpdateInstanceQosParam;
import cn.veryai.arcreactor.web.params.RebuildInstanceParam;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instances")
@RequiredArgsConstructor
public class InstanceController {
    private final InstanceService instanceService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletionStage<CreateInstanceResult> createInstance(@Valid @RequestBody CreateInstanceParam param) {
        return instanceService.create(param);
    }

    @GetMapping("/{instanceId}")
    public CompletionStage<InstanceView> getInstance(@PathVariable("instanceId") String instanceId) {
        return instanceService.get(instanceId);
    }

    @DeleteMapping("/{instanceId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletionStage<Void> deleteInstance(@PathVariable("instanceId") String instanceId) {
        return instanceService.delete(instanceId);
    }

    @GetMapping("/{instanceId}/forwardings")
    public List<PortForwardingEntity> getForwardings(@PathVariable("instanceId") String instanceId) {
        return instanceService.getForwardings(instanceId);
    }

    @PostMapping("/{instanceId}/reboot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletionStage<InstanceMutationResult> rebootInstance(@PathVariable("instanceId") String instanceId) {
        return instanceService.reboot(instanceId);
    }

    @PostMapping("/{instanceId}/rebuild")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletionStage<InstanceMutationResult> rebuildInstance(@PathVariable("instanceId") String instanceId,
            @Valid @RequestBody RebuildInstanceParam param) {
        return instanceService.rebuild(instanceId, param);
    }

    @PostMapping("/{instanceId}/forwardings")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletionStage<InstanceMutationResult> createForwarding(@PathVariable("instanceId") String instanceId,
            @Valid @RequestBody CreateUserForwardingParam param) {
        return instanceService.createForwarding(instanceId, param);
    }

    @DeleteMapping("/{instanceId}/forwardings/{claimId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletionStage<InstanceMutationResult> deleteForwarding(@PathVariable("instanceId") String instanceId,
            @PathVariable("claimId") String claimId) {
        return instanceService.deleteForwarding(instanceId, claimId);
    }

    @GetMapping("/{instanceId}/qos/history")
    public List<InstanceQosRecordEntity> getQosHistory(@PathVariable("instanceId") String instanceId) {
        return instanceService.getQosHistory(instanceId);
    }

    @GetMapping("/{instanceId}/qos/current")
    public RespWrapper<InstanceCurrentQosEntity, Object> getCurrentQos(@PathVariable("instanceId") String instanceId) {
        // Preserve a null result when no current record exists, instead of the generic empty-object response.
        return RespWrapper.success(instanceService.getCurrentQos(instanceId));
    }

    @PutMapping("/{instanceId}/qos")
    public CompletionStage<ResponseEntity<RespWrapper<InstanceMutationResult, Object>>> updateQos(
            @PathVariable("instanceId") String instanceId,
            @Valid @RequestBody UpdateInstanceQosParam param) {
        return instanceService.updateQos(instanceId, param).thenApply(result -> ResponseEntity
                .status(result.operationId() == null ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(RespWrapper.success(result)));
    }
}
