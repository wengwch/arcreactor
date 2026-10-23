package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.enums.HypervisorStatus;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.repo.HypervisorHostRepo;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import lombok.RequiredArgsConstructor;
import org.openstack4j.model.compute.ext.Hypervisor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataSyncService {
    private final OpenStackClient openStackClient;
    private final RegionRepo regionRepo;
    private final OpenstackClusterRepo clusterRepo;
    private final HypervisorHostRepo hypervisorHostRepo;
    private final PlatformTransactionManager transactionManager;

    public record HypervisorSyncResult(String regionId, int synced) {}

    public HypervisorSyncResult syncHypervisor(String id) {
        HypervisorHostEntity existing = hypervisorHostRepo.findById(id);
        if (existing == null) {
            throw new BaseException(ErrorCode.HYPERVISOR_HOST_NOT_FOUND);
        }
        String regionId = existing.getRegionId();
        if (existing.getHypervisorStatus() != HypervisorStatus.MAINTENANCE) {
            return new HypervisorSyncResult(regionId, 0);
        }
        if (existing.getOsHypervisorId() == null || existing.getOsHypervisorId().isBlank()) {
            throw new BaseException(ErrorCode.HYPERVISOR_OPENSTACK_ID_REQUIRED);
        }
        requireSyncRegion(regionId);

        Hypervisor hypervisor;
        try {
            hypervisor = openStackClient.getHypervisor(regionId, existing.getOsHypervisorId());
        } catch (RuntimeException exception) {
            throw new BaseException(ErrorCode.OPENSTACK_HYPERVISOR_GET_FAILED, exception);
        }
        if (hypervisor == null) {
            throw new BaseException(ErrorCode.OPENSTACK_HYPERVISOR_NOT_FOUND);
        }
        if (!existing.getOsHypervisorId().equals(hypervisor.getId())) {
            throw new BaseException(ErrorCode.OPENSTACK_HYPERVISOR_ID_MISMATCH);
        }
        HypervisorHostEntity host = toHost(regionId, hypervisor);
        host.setId(existing.getId());
        return syncHosts(regionId, List.of(host));
    }

    public HypervisorSyncResult syncHypervisors(String regionId) {
        requireSyncRegion(regionId);

        List<? extends Hypervisor> hypervisors;
        try {
            hypervisors = openStackClient.listHypervisors(regionId);
        } catch (RuntimeException exception) {
            throw new BaseException(ErrorCode.OPENSTACK_HYPERVISOR_LIST_FAILED, exception);
        }
        if (hypervisors == null) {
            throw new BaseException(ErrorCode.OPENSTACK_HYPERVISOR_LIST_MISSING);
        }

        Set<String> ids = new HashSet<>();
        List<HypervisorHostEntity> hosts = hypervisors.stream().map(hypervisor -> {
            HypervisorHostEntity host = toHost(regionId, hypervisor);
            if (!ids.add(host.getOsHypervisorId())) {
                throw new BaseException(ErrorCode.OPENSTACK_HYPERVISOR_DUPLICATE_IDS);
            }
            return host;
        }).sorted(Comparator.comparing(HypervisorHostEntity::getOsHypervisorId)).toList();
        return syncHosts(regionId, hosts);
    }

    private void requireSyncRegion(String regionId) {
        RegionEntity region = regionRepo.findById(regionId);
        if (region == null) {
            throw new BaseException(ErrorCode.REGION_NOT_FOUND);
        }
        OpenstackClusterEntity cluster = clusterRepo.findById(region.getClusterId());
        if (cluster == null) {
            throw new BaseException(ErrorCode.OPENSTACK_CLUSTER_NOT_FOUND);
        }
        if (cluster.getAdminDefaultProjectId() == null || cluster.getAdminDefaultProjectId().isBlank()) {
            throw new BaseException(ErrorCode.OPENSTACK_ADMIN_PROJECT_REQUIRED);
        }
    }

    private HypervisorSyncResult syncHosts(String regionId, List<HypervisorHostEntity> hosts) {
        // Fetch and validate the entire snapshot before opening the database transaction.
        // A failed write rolls back the entire region; missing upstream hosts are retained.
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                int synced = 0;
                for (HypervisorHostEntity host : hosts) {
                    if (hypervisorHostRepo.upsertFromOpenstack(host)) {
                        synced++;
                    }
                }
                return new HypervisorSyncResult(regionId, synced);
            });
        } catch (DataIntegrityViolationException exception) {
            throw new BaseException(ErrorCode.HYPERVISOR_SYNC_CONSTRAINT_VIOLATION, exception);
        }
    }

    private HypervisorHostEntity toHost(String regionId, Hypervisor hypervisor) {
        if (hypervisor == null || hypervisor.getId() == null || hypervisor.getId().isBlank()
                || hypervisor.getHypervisorHostname() == null || hypervisor.getHypervisorHostname().isBlank()) {
            throw new BaseException(ErrorCode.OPENSTACK_HYPERVISOR_INVALID);
        }
        HypervisorHostEntity host = new HypervisorHostEntity();
        host.setId(hypervisor.getId());
        host.setName(hypervisor.getHypervisorHostname());
        host.setHostname(hypervisor.getHypervisorHostname());
        host.setIp(hypervisor.getHostIP());
        host.setOsHypervisorId(hypervisor.getId());
        host.setVcpus(hypervisor.getVirtualCPU());
        host.setUsedVcpus(hypervisor.getVirtualUsedCPU());
        // Application memory uses GB (1024 MB); convert Nova's MB at the boundary.
        host.setRam(hypervisor.getLocalMemory() / 1024);
        host.setUsedRam(hypervisor.getLocalMemoryUsed() / 1024);
        host.setDisk(hypervisor.getLocalDisk());
        host.setUsedDisk(hypervisor.getLocalDiskUsed());
        host.setRunningVms(hypervisor.getRunningVM());
        host.setStatus(hypervisor.getStatus());
        host.setState(hypervisor.getState());
        host.setEnabled(true);
        host.setRegionId(regionId);
        // hypervisorType references the local hardware dictionary, not Nova's QEMU/KVM type.
        // Nova's hypervisor API does not report GPU capacity or GPU usage.
        return host;
    }
}
