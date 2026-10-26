package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.service.InstanceService;
import cn.veryai.arcreactor.vo.CreateInstanceResult;
import cn.veryai.arcreactor.web.params.CreateInstanceParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
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
    public CreateInstanceResult createInstance(@Valid @RequestBody CreateInstanceParam param) {
        return instanceService.create(param);
    }
}
