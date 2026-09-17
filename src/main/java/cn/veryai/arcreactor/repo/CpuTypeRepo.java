package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.CpuTypeEntity;
import cn.veryai.arcreactor.mapper.CpuTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for CpuType; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class CpuTypeRepo {
    private final CpuTypeMapper mapper;

    public int insert(CpuTypeEntity entity) {
        return mapper.insert(entity);
    }

    public CpuTypeEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<CpuTypeEntity> findAll() {
        return mapper.findAll();
    }

    public int update(CpuTypeEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
