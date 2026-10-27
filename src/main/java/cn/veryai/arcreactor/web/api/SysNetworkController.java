package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.SysNetworkEntity;
import cn.veryai.arcreactor.service.SysNetworkService;
import cn.veryai.arcreactor.web.params.SaveSysNetworkParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sys-networks")
@RequiredArgsConstructor
public class SysNetworkController {
    private final SysNetworkService sysNetworkService;

    @PostMapping
    public ResponseEntity<SysNetworkEntity> create(@Valid @RequestBody SaveSysNetworkParam param) {
        SysNetworkEntity entity = sysNetworkService.create(param);
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    @GetMapping
    public List<SysNetworkEntity> list(@RequestParam(value = "regionId", required = false) String regionId) {
        return sysNetworkService.list(regionId);
    }

    @GetMapping("/{id}")
    public SysNetworkEntity get(@PathVariable("id") String id) {
        return sysNetworkService.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        sysNetworkService.delete(id);
    }
}
