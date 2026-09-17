package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.mapper.RegionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for Region; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class RegionRepo {
    private final RegionMapper mapper;

    public int insert(RegionEntity entity) {
        return mapper.insert(entity);
    }

    public RegionEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<RegionEntity> findAll() {
        return mapper.findAll();
    }

    public List<RegionEntity> findByClusterId(String clusterId) {
        return mapper.findByClusterId(clusterId);
    }

    public int update(RegionEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
