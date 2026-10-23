package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.service.DataSyncService;
import cn.veryai.arcreactor.service.DataSyncService.HypervisorSyncResult;
import cn.veryai.arcreactor.service.HypervisorHostService;
import cn.veryai.arcreactor.web.params.SetHypervisorMaintenancePlanParam;
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
  private final DataSyncService dataSyncService;

  @GetMapping
  public List<HypervisorHostEntity> list() {
    return hypervisorHostService.list();
  }

  @GetMapping("/{id}")
  public HypervisorHostEntity get(@PathVariable("id") String id) {
    return hypervisorHostService.get(id);
  }

  @PostMapping("/{id}/sync")
  public HypervisorSyncResult sync(@PathVariable("id") String id) {
    return dataSyncService.syncHypervisor(id);
  }

  @PutMapping("/{id}/maintenance-plan")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void setMaintenancePlan(
      @PathVariable("id") String id,
      @Valid @RequestBody SetHypervisorMaintenancePlanParam param) {
    hypervisorHostService.setMaintenancePlan(id, param.getExpectedMaintenanceTime());
  }

  @PutMapping("/{id}/maintenance")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void setMaintenance(@PathVariable("id") String id) {
    hypervisorHostService.setMaintenance(id);
  }

  @PutMapping("/{id}/enable")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void enable(@PathVariable("id") String id) {
    hypervisorHostService.enable(id);
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
