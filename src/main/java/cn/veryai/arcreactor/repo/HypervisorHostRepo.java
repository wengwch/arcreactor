package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.enums.HypervisorStatus;
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
    public boolean upsertFromOpenstack(HypervisorHostEntity entity) {
        mapper.upsertFromOpenstack(entity);
        // MySQL's affected-row count cannot reliably distinguish a skipped row from unchanged data.
        return mapper.findStatusByOpenstackIdForUpdate(entity.getRegionId(), entity.getOsHypervisorId())
                == HypervisorStatus.MAINTENANCE;
    }

    public HypervisorHostEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<HypervisorHostEntity> findAll() {
        return mapper.findAll();
    }

    public int update(HypervisorHostEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
