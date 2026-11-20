package cn.veryai.arcreactor.actor.instance.projection.mybatis;

import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.instance.model.InstanceServerOperation;
import cn.veryai.arcreactor.actor.instance.model.PreparedDependencies;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import java.sql.Timestamp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstanceProjectionMapper {
    int upsertCreated(
            @Param("instanceId") String instanceId,
            @Param("requestId") String requestId,
            @Param("spec") InstanceSpec spec,
            @Param("phase") InstancePhase phase,
            @Param("version") long version,
            @Param("createdAt") Timestamp createdAt,
            @Param("updatedAt") Timestamp updatedAt);

    int updatePhase(
            @Param("instanceId") String instanceId,
            @Param("phase") InstancePhase phase,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateVersion(
            @Param("instanceId") String instanceId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateDependencies(
            @Param("instanceId") String instanceId,
            @Param("phase") InstancePhase phase,
            @Param("dependencies") PreparedDependencies dependencies,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateScheduling(
            @Param("instanceId") String instanceId,
            @Param("phase") InstancePhase phase,
            @Param("reservationId") String reservationId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateHypervisor(
            @Param("instanceId") String instanceId,
            @Param("phase") InstancePhase phase,
            @Param("hypervisorId") String hypervisorId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateSecurityGroup(
            @Param("instanceId") String instanceId,
            @Param("phase") InstancePhase phase,
            @Param("osSecurityGroupId") String osSecurityGroupId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateNetwork(
            @Param("instanceId") String instanceId,
            @Param("phase") InstancePhase phase,
            @Param("network") PreparedNetwork network,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateQosState(
            @Param("instanceId") String instanceId,
            @Param("qosUpdateJson") String qosUpdateJson,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateQos(
            @Param("instanceId") String instanceId,
            @Param("network") PreparedNetwork network,
            @Param("qosUpdateJson") String qosUpdateJson,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateNovaOperation(
            @Param("instanceId") String instanceId,
            @Param("phase") InstancePhase phase,
            @Param("operationId") String operationId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateServerOperation(
            @Param("instanceId") String instanceId,
            @Param("phase") InstancePhase phase,
            @Param("operation") InstanceServerOperation operation,
            @Param("novaStatus") String novaStatus,
            @Param("osImageId") String osImageId,
            @Param("failureCode") String failureCode,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateNovaServer(
            @Param("instanceId") String instanceId,
            @Param("phase") InstancePhase phase,
            @Param("osServerId") String osServerId,
            @Param("novaStatus") String novaStatus,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateNovaDeletion(
            @Param("instanceId") String instanceId,
            @Param("phase") InstancePhase phase,
            @Param("osServerId") String osServerId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateFailure(
            @Param("instanceId") String instanceId,
            @Param("phase") InstancePhase phase,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);
}
