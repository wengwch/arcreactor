package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.PortForwardingEntity;
import cn.veryai.arcreactor.mapper.PortForwardingMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PortForwardingRepo {
    private final PortForwardingMapper mapper;

    public List<PortForwardingEntity> findByInstanceId(String instanceId) {
        return mapper.findByInstanceId(instanceId);
    }
}
