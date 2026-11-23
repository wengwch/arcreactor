package cn.veryai.arcreactor.entity;

import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** Read-only last successfully activated QoS and its expiry phase. */
@Data
@FieldNameConstants(innerTypeName = "F")
public class InstanceCurrentQosEntity {
    private String instanceId;
    private String operationId;
    private InstanceQosUpdate.Phase phase;
    private OffsetDateTime expiresAt;
    private OffsetDateTime switchedAt;
    private String replacedQosId;
    private long resourceVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
