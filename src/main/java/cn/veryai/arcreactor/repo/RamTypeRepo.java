package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.RamTypeEntity;
import cn.veryai.arcreactor.mapper.RamTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for RamType; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class RamTypeRepo {
    private final RamTypeMapper mapper;

    public int insert(RamTypeEntity entity) {
        return mapper.insert(entity);
    }

    public RamTypeEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<RamTypeEntity> findAll() {
        return mapper.findAll();
    }

    public int update(RamTypeEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
