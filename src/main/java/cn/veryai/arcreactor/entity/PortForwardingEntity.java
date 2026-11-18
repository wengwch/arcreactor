package cn.veryai.arcreactor.entity;

import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** Read-only FIP forwarding lifecycle, including failed and deleted mappings. */
@Data
@FieldNameConstants(innerTypeName = "F")
public class PortForwardingEntity {
    private String floatingIpId;
    private String regionId;
    private String osFloatingIpId;
    private String claimId;
    private String instanceId;
    private String userId;
    private String osProjectId;
    private ForwardingKind kind;
    private String floatingIp;
    private String domain;
    private String osInternalPortId;
    private String fixedIp;
    private Integer internalPort;
    private Integer externalPort;
    private String osTcpForwardingId;
    private String osUdpForwardingId;
    private ForwardingClaim.Phase phase;
    private String failureMessage;
    private long resourceVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
