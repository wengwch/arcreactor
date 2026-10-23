package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.service.HypervisorHostService;
import cn.veryai.arcreactor.web.params.UpdateHypervisorHostParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hypervisor-hosts")
@RequiredArgsConstructor
public class HypervisorHostController {
  private final HypervisorHostService hypervisorHostService;

  @GetMapping
  public List<HypervisorHostEntity> list() {
    return hypervisorHostService.list();
  }

  @GetMapping("/{id}")
  public HypervisorHostEntity get(@PathVariable("id") String id) {
    return hypervisorHostService.get(id);
  }

  @PutMapping("/{id}")
  public HypervisorHostEntity update(
      @PathVariable("id") String id, @Valid @RequestBody UpdateHypervisorHostParam param) {
    return hypervisorHostService.update(id, param);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable("id") String id) {
    hypervisorHostService.delete(id);
  }
}
