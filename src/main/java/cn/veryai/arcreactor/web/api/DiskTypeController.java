package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.DiskTypeEntity;
import cn.veryai.arcreactor.service.DiskTypeService;
import cn.veryai.arcreactor.web.params.SaveDiskTypeParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/disk-types")
@RequiredArgsConstructor
public class DiskTypeController {
    private final DiskTypeService service;

    @PostMapping
    public ResponseEntity<DiskTypeEntity> create(@Valid @RequestBody SaveDiskTypeParam param) {
        DiskTypeEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<DiskTypeEntity> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public DiskTypeEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public DiskTypeEntity update(@PathVariable("id") String id,
                                         @Valid @RequestBody SaveDiskTypeParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
