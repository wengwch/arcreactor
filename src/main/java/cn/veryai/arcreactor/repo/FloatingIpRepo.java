package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.FloatingIpEntity;
import cn.veryai.arcreactor.mapper.FloatingIpMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FloatingIpRepo {
    private final FloatingIpMapper mapper;

    public int insert(FloatingIpEntity entity) {
        return mapper.insert(entity);
    }

    public FloatingIpEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<FloatingIpEntity> findByRegionId(String regionId) {
        return mapper.findByRegionId(regionId);
    }
}
