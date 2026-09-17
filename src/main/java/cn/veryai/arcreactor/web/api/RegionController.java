package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.service.RegionService;
import cn.veryai.arcreactor.web.params.CreateRegionParam;
import cn.veryai.arcreactor.web.params.SaveRegionParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {
    private final RegionService service;

    @PostMapping
    public ResponseEntity<RegionEntity> create(@Valid @RequestBody CreateRegionParam param) {
        RegionEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<RegionEntity> list(@RequestParam(value = "clusterId", required = false) String clusterId) {
        return service.list(clusterId);
    }

    @GetMapping("/{id}")
    public RegionEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public RegionEntity update(@PathVariable("id") String id, @Valid @RequestBody SaveRegionParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
