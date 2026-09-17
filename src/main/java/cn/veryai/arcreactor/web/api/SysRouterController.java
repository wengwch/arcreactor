package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.SysRouterEntity;
import cn.veryai.arcreactor.service.SysRouterService;
import cn.veryai.arcreactor.web.params.SaveSysRouterParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sys-routers")
@RequiredArgsConstructor
public class SysRouterController {
    private final SysRouterService service;

    @PostMapping
    public ResponseEntity<SysRouterEntity> create(@Valid @RequestBody SaveSysRouterParam param) {
        SysRouterEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<SysRouterEntity> list(@RequestParam(value = "regionId", required = false) String regionId) {
        return service.list(regionId);
    }

    @GetMapping("/{id}")
    public SysRouterEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public SysRouterEntity update(@PathVariable("id") String id,
                                         @Valid @RequestBody SaveSysRouterParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
