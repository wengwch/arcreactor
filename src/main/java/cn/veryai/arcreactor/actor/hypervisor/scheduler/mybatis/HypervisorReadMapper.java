package cn.veryai.arcreactor.actor.hypervisor.scheduler.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HypervisorReadMapper {
    List<HypervisorResourceRow> findCandidates(
            @Param("vcpus") int vcpus,
            @Param("memoryGb") long memoryGb,
            @Param("availabilityZone") String availabilityZone,
            @Param("gpuCount") int gpuCount,
            @Param("limit") int limit);
}
