package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.CpuTypeEntity;
import cn.veryai.arcreactor.service.CpuTypeService;
import cn.veryai.arcreactor.web.params.SaveCpuTypeParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cpu-types")
@RequiredArgsConstructor
public class CpuTypeController {
    private final CpuTypeService service;

    @PostMapping
    public ResponseEntity<CpuTypeEntity> create(@Valid @RequestBody SaveCpuTypeParam param) {
        CpuTypeEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<CpuTypeEntity> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CpuTypeEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public CpuTypeEntity update(@PathVariable("id") String id,
                                         @Valid @RequestBody SaveCpuTypeParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
