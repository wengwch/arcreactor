package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.InstanceViewEntity;
import cn.veryai.arcreactor.service.InstanceQueryService;
import cn.veryai.arcreactor.web.params.QueryUserInstancesParam;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}/instances")
@RequiredArgsConstructor
public class UserInstanceController {
    private final InstanceQueryService instanceQueryService;

    @GetMapping
    public List<InstanceViewEntity> list(@PathVariable("userId") String userId,
            @Valid @ModelAttribute QueryUserInstancesParam param) {
        return instanceQueryService.list(userId, param.getRegionId(), param.getPhase());
    }

    @GetMapping("/{instanceId}")
    public InstanceViewEntity get(@PathVariable("userId") String userId,
            @PathVariable("instanceId") String instanceId) {
        return instanceQueryService.get(userId, instanceId);
    }
}
