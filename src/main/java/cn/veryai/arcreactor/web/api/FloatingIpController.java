package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.FloatingIpEntity;
import cn.veryai.arcreactor.service.FloatingIpService;
import cn.veryai.arcreactor.web.params.SaveFloatingIpParam;
import cn.veryai.arcreactor.web.params.EnableFloatingIpParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/floating-ips")
@RequiredArgsConstructor
public class FloatingIpController {
    private final FloatingIpService floatingIpService;

    @PostMapping
    public ResponseEntity<FloatingIpEntity> create(@Valid @RequestBody SaveFloatingIpParam param) {
        FloatingIpEntity entity = floatingIpService.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @PutMapping("/{id}/enable")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enable(@PathVariable("id") String id,
            @Valid @RequestBody(required = false) EnableFloatingIpParam param) {
        floatingIpService.enable(id, param);
    }

    @PutMapping("/{id}/disable")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void disable(@PathVariable("id") String id) {
        floatingIpService.disable(id);
    }
}
