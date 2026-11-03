package cn.veryai.arcreactor.actor.hypervisor.projection.mybatis;

import cn.veryai.arcreactor.actor.hypervisor.model.Allocation;
import cn.veryai.arcreactor.actor.hypervisor.model.ResourceCapacity;
import cn.veryai.arcreactor.actor.hypervisor.model.ResourceRequest;
import cn.veryai.arcreactor.enums.HypervisorStatus;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HypervisorProjectionMapper {
    int upsertCapacity(
            @Param("hypervisorId") String hypervisorId,
            @Param("capacity") ResourceCapacity capacity,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int applyResourceDelta(
            @Param("hypervisorId") String hypervisorId,
            @Param("resources") ResourceRequest resources,
            @Param("reservationMultiplier") int reservationMultiplier,
            @Param("allocationMultiplier") int allocationMultiplier,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateStatus(
            @Param("hypervisorId") String hypervisorId,
            @Param("status") HypervisorStatus status,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateHostStatus(
            @Param("hypervisorId") String hypervisorId,
            @Param("status") HypervisorStatus status,
            @Param("expectedMaintenanceTime") LocalDateTime expectedMaintenanceTime);

    int refreshHostRunningVms(@Param("hypervisorId") String hypervisorId);

    int upsertAllocation(
            @Param("hypervisorId") String hypervisorId,
            @Param("allocation") Allocation allocation,
            @Param("status") String status,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);
}
