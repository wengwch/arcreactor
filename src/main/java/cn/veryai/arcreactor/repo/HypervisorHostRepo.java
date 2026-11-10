package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.mapper.HypervisorHostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for HypervisorHost; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class HypervisorHostRepo {
    private final HypervisorHostMapper mapper;

    public int insert(HypervisorHostEntity entity) {
        return mapper.insert(entity);
    }

    /** Must run inside the sync transaction so the upsert's row lock is retained. */
    public int upsertFromOpenstack(HypervisorHostEntity entity) {
        return mapper.upsertFromOpenstack(entity);
    }

    public HypervisorHostEntity findByOpenstackIdForUpdate(String regionId, String osHypervisorId) {
        return mapper.findByOpenstackIdForUpdate(regionId, osHypervisorId);
    }

    public HypervisorHostEntity findById(String id) {
        return mapper.findById(id);
    }

    /** Must run inside the configuration update transaction to retain the row lock. */
    public HypervisorHostEntity findByIdForUpdate(String id) {
        return mapper.findByIdForUpdate(id);
    }

    public List<HypervisorHostEntity> findAll() {
        return mapper.findAll();
    }

    public List<HypervisorHostEntity> findCandidates(
            int vcpus, long memoryGb, long diskGb, String availabilityZone, int gpuCount,
            String regionId, String hypervisorType, String hostname, int limit) {
        return mapper.findCandidates(vcpus, memoryGb, diskGb, availabilityZone, gpuCount,
                regionId, hypervisorType, hostname, limit);
    }

    public int update(HypervisorHostEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
