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

    public List<HypervisorResourceRow> findCandidates(int vcpus, long memoryMb, String availabilityZone,
                                                     String requiredTraitsJson, int gpuCount, int limit) {
        return mapper.findCandidates(vcpus, memoryMb, availabilityZone, requiredTraitsJson, gpuCount, limit);
    }
}
