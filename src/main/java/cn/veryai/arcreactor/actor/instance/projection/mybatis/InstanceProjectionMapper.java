package cn.veryai.arcreactor.actor.instance.projection.mybatis;

import java.sql.Timestamp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstanceProjectionMapper {
    int upsertCreated(
            @Param("instanceId") String instanceId,
            @Param("requestId") String requestId,
            @Param("phase") String phase,
            @Param("version") long version,
            @Param("createdAt") Timestamp createdAt,
            @Param("updatedAt") Timestamp updatedAt);

    int updatePhase(
            @Param("instanceId") String instanceId,
            @Param("phase") String phase,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateHypervisor(
            @Param("instanceId") String instanceId,
            @Param("phase") String phase,
            @Param("hypervisorId") String hypervisorId,
            @Param("reservationId") String reservationId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateNovaOperation(
            @Param("instanceId") String instanceId,
            @Param("phase") String phase,
            @Param("operationId") String operationId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateNovaServer(
            @Param("instanceId") String instanceId,
            @Param("phase") String phase,
            @Param("serverId") String serverId,
            @Param("novaStatus") String novaStatus,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateFailure(
            @Param("instanceId") String instanceId,
            @Param("phase") String phase,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);
}
