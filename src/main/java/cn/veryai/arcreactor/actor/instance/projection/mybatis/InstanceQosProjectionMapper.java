package cn.veryai.arcreactor.actor.instance.projection.mybatis;

import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import java.sql.Timestamp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstanceQosProjectionMapper {
    Long findRecordVersion(@Param("instanceId") String instanceId, @Param("operationId") String operationId);

    int cancelOutstanding(@Param("instanceId") String instanceId,
            @Param("version") long version, @Param("updatedAt") Timestamp updatedAt);

    int cancelReplaced(@Param("instanceId") String instanceId, @Param("operationId") String operationId,
            @Param("switchedAt") Timestamp switchedAt, @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int insertRequested(@Param("instanceId") String instanceId, @Param("update") InstanceQosUpdate update,
            @Param("expiresAt") Timestamp expiresAt, @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int upsertCurrent(@Param("instanceId") String instanceId, @Param("update") InstanceQosUpdate update,
            @Param("expiresAt") Timestamp expiresAt, @Param("switchedAt") Timestamp switchedAt,
            @Param("replacedQosId") String replacedQosId, @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int activateRecord(@Param("instanceId") String instanceId, @Param("update") InstanceQosUpdate update,
            @Param("switchedAt") Timestamp switchedAt, @Param("replacedQosId") String replacedQosId,
            @Param("version") long version, @Param("updatedAt") Timestamp updatedAt);

    int updateRecord(@Param("instanceId") String instanceId, @Param("update") InstanceQosUpdate update,
            @Param("version") long version, @Param("updatedAt") Timestamp updatedAt);

    int updateCurrent(@Param("instanceId") String instanceId, @Param("update") InstanceQosUpdate update,
            @Param("version") long version, @Param("updatedAt") Timestamp updatedAt);

    int deleteCurrent(@Param("instanceId") String instanceId, @Param("operationId") String operationId,
            @Param("version") long version);
}
