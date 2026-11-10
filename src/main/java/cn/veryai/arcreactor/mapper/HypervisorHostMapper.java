package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HypervisorHostMapper {
    int insert(HypervisorHostEntity entity);

    int upsertFromOpenstack(HypervisorHostEntity entity);

    HypervisorHostEntity findByOpenstackIdForUpdate(@Param("regionId") String regionId,
                                                     @Param("osHypervisorId") String osHypervisorId);

    HypervisorHostEntity findById(@Param("id") String id);

    HypervisorHostEntity findByIdForUpdate(@Param("id") String id);

    List<HypervisorHostEntity> findAll();

    List<HypervisorHostEntity> findCandidates(
            @Param("vcpus") int vcpus,
            @Param("memoryGb") long memoryGb,
            @Param("diskGb") long diskGb,
            @Param("availabilityZone") String availabilityZone,
            @Param("gpuCount") int gpuCount,
            @Param("regionId") String regionId,
            @Param("hypervisorType") String hypervisorType,
            @Param("hostname") String hostname,
            @Param("limit") int limit);

    int update(HypervisorHostEntity entity);

    int deleteById(@Param("id") String id);
}
