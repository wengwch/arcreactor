package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.mybatis.HypervisorReadMapper;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.mybatis.HypervisorResourceRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RequiredArgsConstructor
public class HypervisorReadRepo {
    private final HypervisorReadMapper mapper;

    public List<HypervisorResourceRow> findCandidates(
            int vcpus, long memoryGb, String availabilityZone, int gpuCount,
            String regionId, String hypervisorType, int limit) {
        return mapper.findCandidates(vcpus, memoryGb, availabilityZone, gpuCount, regionId, hypervisorType, limit);
    }
}
