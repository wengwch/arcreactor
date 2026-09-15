package cn.veryai.arcreactor.actor.hypervisor.projection.repository;

import cn.veryai.arcreactor.actor.hypervisor.event.HypervisorEvent;
import cn.veryai.arcreactor.mapper.HypervisorProjectionMapper;
import java.time.Instant;

public interface HypervisorReadRepository {
    void apply(HypervisorProjectionMapper mapper, String hypervisorId, HypervisorEvent event,
            long sequenceNumber, Instant eventTimestamp);
}
