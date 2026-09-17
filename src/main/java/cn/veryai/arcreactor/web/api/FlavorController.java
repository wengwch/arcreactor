package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.FlavorEntity;
import cn.veryai.arcreactor.service.FlavorService;
import cn.veryai.arcreactor.web.params.SaveFlavorParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flavors")
@RequiredArgsConstructor
public class FlavorController {
    private final FlavorService service;

    @PostMapping
    public ResponseEntity<FlavorEntity> create(@Valid @RequestBody SaveFlavorParam param) {
        FlavorEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<FlavorEntity> list(@RequestParam(value = "regionId", required = false) String regionId) {
        return service.list(regionId);
    }

    @GetMapping("/{id}")
    public FlavorEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public FlavorEntity update(@PathVariable("id") String id,
                                         @Valid @RequestBody SaveFlavorParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
