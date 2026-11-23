package cn.veryai.arcreactor.entity;

import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import cn.veryai.arcreactor.actor.instance.model.InstanceServerOperation;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** Read-only instance projection; credentials are never stored in this table. */
@Data
@FieldNameConstants(innerTypeName = "F")
public class InstanceViewEntity {
    private String instanceId;
    private String requestId;
    private String name;
    private String description;
    private String userId;
    private String clusterId;
    private String regionId;
    private String osProjectId;
    private String osImageId;
    private String osFlavorId;
    private String hypervisorType;
    private Integer vcpus;
    private Long memoryGb;
    private Long diskGb;
    private Integer gpuCount;
    private String shareId;
    private String osParentNetworkId;
    private String osParentSubnetId;
    private String osSubNetworkId;
    private String osSubSubnetId;
    private String osSubFloatingNetworkId;
    private Integer segmentationId;
    private InstancePhase phase;
    private String hypervisorId;
    private String hostname;
    private String reservationId;
    private String osNovaServerId;
    private String novaStatus;
    private String osSecurityGroupId;
    private String osParentPortId;
    private String parentMacAddress;
    private String parentFixedIp;
    private String osParentQosPolicyId;
    private String osParentQosIngressRuleId;
    private String osParentQosEgressRuleId;
    private Integer parentMaxKbps;
    private Integer parentMaxBurstKbits;
    private String osSubPortId;
    private String subFixedIp;
    private String osSubQosPolicyId;
    private String osSubQosIngressRuleId;
    private String osSubQosEgressRuleId;
    private Integer subMaxKbps;
    private Integer subMaxBurstKbits;
    private String osSubFloatingIpId;
    private String subFloatingIpAddress;
    private String osTrunkId;
    private InstanceQosUpdate qosUpdate;
    private InstanceServerOperation serverOperation;
    private String failureCode;
    private String failureMessage;
    private long workflowVersion;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
