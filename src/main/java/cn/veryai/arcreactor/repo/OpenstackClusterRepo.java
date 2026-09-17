package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.mapper.OpenstackClusterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for OpenstackCluster; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class OpenstackClusterRepo {
    private final OpenstackClusterMapper mapper;

    public int insert(OpenstackClusterEntity entity) {
        return mapper.insert(entity);
    }

    public OpenstackClusterEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<OpenstackClusterEntity> findAll() {
        return mapper.findAll();
    }

    public int update(OpenstackClusterEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
