package cn.veryai.arcreactor.entity;

import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** Read-only QoS request history, including cancelled and expired records. */
@Data
@FieldNameConstants(innerTypeName = "F")
public class InstanceQosRecordEntity {
    private String instanceId;
    private String operationId;
    private InstanceQosUpdate.Phase phase;
    private OffsetDateTime expiresAt;
    private OffsetDateTime switchedAt;
    private String replacedQosId;
    private Integer parentMaxKbps;
    private Integer parentMaxBurstKbits;
    private Integer subMaxKbps;
    private Integer subMaxBurstKbits;
    private Integer expiryParentMaxKbps;
    private Integer expiryParentMaxBurstKbits;
    private Integer expirySubMaxKbps;
    private Integer expirySubMaxBurstKbits;
    private String failureMessage;
    private long resourceVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime expiredAt;
}
