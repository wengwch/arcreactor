package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.InstanceCurrentQosEntity;
import cn.veryai.arcreactor.entity.InstanceQosRecordEntity;
import cn.veryai.arcreactor.mapper.InstanceQosMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InstanceQosRepo {
    private final InstanceQosMapper mapper;

    public List<InstanceQosRecordEntity> findHistoryByInstanceId(String instanceId) {
        return mapper.findHistoryByInstanceId(instanceId);
    }

    public InstanceCurrentQosEntity findCurrentByInstanceId(String instanceId) {
        return mapper.findCurrentByInstanceId(instanceId);
    }
}
