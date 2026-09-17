package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.GpuTypeEntity;
import cn.veryai.arcreactor.service.GpuTypeService;
import cn.veryai.arcreactor.web.params.SaveGpuTypeParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gpu-types")
@RequiredArgsConstructor
public class GpuTypeController {
    private final GpuTypeService service;

    @PostMapping
    public ResponseEntity<GpuTypeEntity> create(@Valid @RequestBody SaveGpuTypeParam param) {
        GpuTypeEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<GpuTypeEntity> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public GpuTypeEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public GpuTypeEntity update(@PathVariable("id") String id,
                                         @Valid @RequestBody SaveGpuTypeParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
