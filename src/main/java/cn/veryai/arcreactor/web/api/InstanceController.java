package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.actor.instance.InstanceLifecycle;
import cn.veryai.arcreactor.service.InstanceService;
import cn.veryai.arcreactor.vo.CreateInstanceResult;
import cn.veryai.arcreactor.web.params.CreateInstanceParam;
import jakarta.validation.Valid;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public CompletionStage<InstanceLifecycle.View> getInstance(@PathVariable("instanceId") String instanceId) {
        return instanceService.get(instanceId);
    }
}
