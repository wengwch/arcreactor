package cn.veryai.arcreactor.web.api;

import cn.veryai.arcreactor.service.DataSyncService;
import cn.veryai.arcreactor.service.DataSyncService.HypervisorSyncResult;
import cn.veryai.arcreactor.web.params.SyncHypervisorParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-sync")
@RequiredArgsConstructor
public class DataSyncController {
    private final DataSyncService dataSyncService;

    @PostMapping("/hypervisors")
    public HypervisorSyncResult syncHypervisors(@Valid @RequestBody SyncHypervisorParam param) {
        return dataSyncService.syncHypervisors(param.getRegionId());
    }
}
