package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.HypervisorTypeEntity;
import cn.veryai.arcreactor.mapper.HypervisorTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for HypervisorType; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class HypervisorTypeRepo {
    private final HypervisorTypeMapper mapper;

    public int insert(HypervisorTypeEntity entity) {
        return mapper.insert(entity);
    }

    public HypervisorTypeEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<HypervisorTypeEntity> findAll() {
        return mapper.findAll();
    }

    public int update(HypervisorTypeEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
