package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.GpuTypeEntity;
import cn.veryai.arcreactor.mapper.GpuTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for GpuType; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class GpuTypeRepo {
    private final GpuTypeMapper mapper;

    public int insert(GpuTypeEntity entity) {
        return mapper.insert(entity);
    }

    public GpuTypeEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<GpuTypeEntity> findAll() {
        return mapper.findAll();
    }

    public int update(GpuTypeEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
