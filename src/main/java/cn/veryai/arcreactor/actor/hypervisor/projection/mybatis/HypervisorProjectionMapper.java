package cn.veryai.arcreactor.actor.hypervisor.projection.mybatis;

import cn.veryai.arcreactor.actor.hypervisor.model.ResourceCapacity;
import cn.veryai.arcreactor.actor.hypervisor.model.ResourceRequest;
import cn.veryai.arcreactor.actor.hypervisor.model.ResourceUsage;
import cn.veryai.arcreactor.enums.HypervisorStatus;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HypervisorProjectionMapper {
    int updateCapacity(
            @Param("hypervisorId") String hypervisorId,
            @Param("capacity") ResourceCapacity capacity,
            @Param("usage") ResourceUsage usage,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    /** A used-resource delta confirms (+1) or releases (-1) one instance; zero leaves its count unchanged. */
    int applyResourceDelta(
            @Param("hypervisorId") String hypervisorId,
            @Param("resources") ResourceRequest resources,
            @Param("reservationMultiplier") int reservationMultiplier,
            @Param("usedMultiplier") int usedMultiplier,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateStatus(
            @Param("hypervisorId") String hypervisorId,
            @Param("status") HypervisorStatus status,
            @Param("expectedMaintenanceTime") LocalDateTime expectedMaintenanceTime,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int advanceVersion(
            @Param("hypervisorId") String hypervisorId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    Long findResourceVersion(@Param("hypervisorId") String hypervisorId);
}
