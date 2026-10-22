package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.repo.HypervisorHostRepo;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import lombok.RequiredArgsConstructor;
import org.openstack4j.model.compute.ext.Hypervisor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DataSyncService {
    private final OpenStackClient openStackClient;
    private final RegionRepo regionRepo;
    private final OpenstackClusterRepo clusterRepo;
    private final HypervisorHostRepo hypervisorHostRepo;
    private final PlatformTransactionManager transactionManager;

    public record HypervisorSyncResult(String regionId, int synced) {}

    public HypervisorSyncResult syncHypervisors(String regionId) {
        RegionEntity region = regionRepo.findById(regionId);
        if (region == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found");
        }
        OpenstackClusterEntity cluster = clusterRepo.findById(region.getClusterId());
        if (cluster == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OpenStack cluster not found");
        }
        if (cluster.getAdminDefaultProjectId() == null || cluster.getAdminDefaultProjectId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cluster adminDefaultProjectId is required");
        }

        List<? extends Hypervisor> hypervisors;
        try {
            hypervisors = openStackClient.listHypervisors(regionId);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to list OpenStack hypervisors", exception);
        }
        if (hypervisors == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenStack returned no hypervisor list");
        }

        Set<String> ids = new HashSet<>();
        List<HypervisorHostEntity> hosts = hypervisors.stream().map(hypervisor -> {
            if (hypervisor == null || hypervisor.getId() == null || hypervisor.getId().isBlank()
                    || hypervisor.getHypervisorHostname() == null || hypervisor.getHypervisorHostname().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenStack returned a hypervisor without ID or hostname");
            }
            if (!ids.add(hypervisor.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenStack returned duplicate hypervisor IDs");
            }
            return toHost(regionId, hypervisor);
        }).sorted(Comparator.comparing(HypervisorHostEntity::getOsHypervisorId)).toList();

        // Fetch and validate the entire snapshot before opening the database transaction.
        // A failed write rolls back the entire region; missing upstream hosts are retained.
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                for (HypervisorHostEntity host : hosts) {
                    hypervisorHostRepo.upsertFromOpenstack(host);
                }
            });
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hypervisor sync violates database constraints", exception);
        }
        return new HypervisorSyncResult(regionId, hosts.size());
    }

    private HypervisorHostEntity toHost(String regionId, Hypervisor hypervisor) {
        HypervisorHostEntity host = new HypervisorHostEntity();
        host.setId(hypervisor.getId());
        host.setName(hypervisor.getHypervisorHostname());
        host.setHostname(hypervisor.getHypervisorHostname());
        host.setIp(hypervisor.getHostIP());
        host.setOsHypervisorId(hypervisor.getId());
        host.setVcpus(hypervisor.getVirtualCPU());
        host.setUsedVcpus(hypervisor.getVirtualUsedCPU());
        // Local host capacities use GiB; Nova reports memory in MiB.
        host.setRam(hypervisor.getLocalMemory() / 1024);
        host.setUsedRam(hypervisor.getLocalMemoryUsed() / 1024);
        host.setDisk(hypervisor.getLocalDisk());
        host.setUsedDisk(hypervisor.getLocalDiskUsed());
        host.setStatus(hypervisor.getStatus());
        host.setState(hypervisor.getState());
        host.setEnabled(true);
        host.setRegionId(regionId);
        // hypervisorType references the local hardware dictionary, not Nova's QEMU/KVM type.
        // Nova's hypervisor API does not report GPU capacity or GPU usage.
        return host;
    }
}
