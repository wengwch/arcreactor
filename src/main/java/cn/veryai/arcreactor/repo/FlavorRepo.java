package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.FlavorEntity;
import cn.veryai.arcreactor.mapper.FlavorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for Flavor; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class FlavorRepo {
    private final FlavorMapper mapper;

    public int insert(FlavorEntity entity) {
        return mapper.insert(entity);
    }

    public FlavorEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<FlavorEntity> findAll() {
        return mapper.findAll();
    }

    public List<FlavorEntity> findByRegionId(String regionId) {
        return mapper.findByRegionId(regionId);
    }

    public int update(FlavorEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
