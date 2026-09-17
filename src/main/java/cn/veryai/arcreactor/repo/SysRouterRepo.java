package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.SysRouterEntity;
import cn.veryai.arcreactor.mapper.SysRouterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for SysRouter; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class SysRouterRepo {
    private final SysRouterMapper mapper;

    public int insert(SysRouterEntity entity) {
        return mapper.insert(entity);
    }

    public SysRouterEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<SysRouterEntity> findAll() {
        return mapper.findAll();
    }

    public List<SysRouterEntity> findByRegionId(String regionId) {
        return mapper.findByRegionId(regionId);
    }

    public int update(SysRouterEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
