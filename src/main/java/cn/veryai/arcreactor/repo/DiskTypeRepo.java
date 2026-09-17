package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.DiskTypeEntity;
import cn.veryai.arcreactor.mapper.DiskTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for DiskType; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class DiskTypeRepo {
    private final DiskTypeMapper mapper;

    public int insert(DiskTypeEntity entity) {
        return mapper.insert(entity);
    }

    public DiskTypeEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<DiskTypeEntity> findAll() {
        return mapper.findAll();
    }

    public int update(DiskTypeEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
