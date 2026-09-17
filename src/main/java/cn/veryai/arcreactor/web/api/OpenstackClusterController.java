package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.service.OpenstackClusterService;
import cn.veryai.arcreactor.web.params.SaveOpenstackClusterParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/openstack-clusters")
@RequiredArgsConstructor
public class OpenstackClusterController {
    private final OpenstackClusterService service;

    @PostMapping
    public ResponseEntity<OpenstackClusterEntity> create(@Valid @RequestBody SaveOpenstackClusterParam param) {
        OpenstackClusterEntity entity = service.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<OpenstackClusterEntity> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public OpenstackClusterEntity get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public OpenstackClusterEntity update(@PathVariable("id") String id,
                                         @Valid @RequestBody SaveOpenstackClusterParam param) {
        return service.update(id, param);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        service.delete(id);
    }
}
