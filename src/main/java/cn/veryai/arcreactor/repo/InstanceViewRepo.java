package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.entity.InstanceViewEntity;
import cn.veryai.arcreactor.mapper.InstanceViewMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Queries the instance read model without contacting its workflow actor. */
@Repository
@RequiredArgsConstructor
public class InstanceViewRepo {
    private final InstanceViewMapper mapper;

    public List<InstanceViewEntity> findByUser(String userId, String regionId, InstancePhase phase) {
        return mapper.findByUser(userId, regionId, phase);
    }

    public InstanceViewEntity findByUserAndId(String userId, String instanceId) {
        return mapper.findByUserAndId(userId, instanceId);
    }
}
