package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.service.HypervisorHostService;
import cn.veryai.arcreactor.web.params.SaveHypervisorHostParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hypervisor-hosts")
@RequiredArgsConstructor
public class HypervisorHostController {
    private final HypervisorHostService service;

    @PostMapping
    public ResponseEntity<HypervisorHostEntity> create(@Valid @RequestBody SaveHypervisorHostParam param) {
        HypervisorHostEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<HypervisorHostEntity> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public HypervisorHostEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public HypervisorHostEntity update(@PathVariable("id") String id,
                                         @Valid @RequestBody SaveHypervisorHostParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
