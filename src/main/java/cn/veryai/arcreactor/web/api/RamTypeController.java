package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.RamTypeEntity;
import cn.veryai.arcreactor.service.RamTypeService;
import cn.veryai.arcreactor.web.params.SaveRamTypeParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ram-types")
@RequiredArgsConstructor
public class RamTypeController {
    private final RamTypeService service;

    @PostMapping
    public ResponseEntity<RamTypeEntity> create(@Valid @RequestBody SaveRamTypeParam param) {
        RamTypeEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<RamTypeEntity> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RamTypeEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public RamTypeEntity update(@PathVariable("id") String id,
                                         @Valid @RequestBody SaveRamTypeParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
