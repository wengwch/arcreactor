package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.HypervisorTypeEntity;
import cn.veryai.arcreactor.service.HypervisorTypeService;
import cn.veryai.arcreactor.web.params.SaveHypervisorTypeParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hypervisor-types")
@RequiredArgsConstructor
public class HypervisorTypeController {
    private final HypervisorTypeService service;

    @PostMapping
    public ResponseEntity<HypervisorTypeEntity> create(@Valid @RequestBody SaveHypervisorTypeParam param) {
        HypervisorTypeEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<HypervisorTypeEntity> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public HypervisorTypeEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public HypervisorTypeEntity update(@PathVariable("id") String id,
                                         @Valid @RequestBody SaveHypervisorTypeParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
