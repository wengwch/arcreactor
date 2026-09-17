package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.SysNetworkEntity;
import cn.veryai.arcreactor.mapper.SysNetworkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for SysNetwork; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class SysNetworkRepo {
    private final SysNetworkMapper mapper;

    public int insert(SysNetworkEntity entity) {
        return mapper.insert(entity);
    }

    public SysNetworkEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<SysNetworkEntity> findAll() {
        return mapper.findAll();
    }

    public List<SysNetworkEntity> findByRegionId(String regionId) {
        return mapper.findByRegionId(regionId);
    }

    public int update(SysNetworkEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
