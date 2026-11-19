package cn.veryai.arcreactor.entity;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpState.Status;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class FloatingIpEntity {
    private String id;
    private String name;
    private String description;
    private String regionId;
    private String extNetId;
    private String osExtNetId;
    private String osProjectId;
    private String osFloatingIpId;
    private String floatingIp;
    private String domain;
    private Status status = Status.DISABLED;
    /** Base64-encoded port bitmap; an empty bitmap is stored as an empty string. */
    private String portMask = "";
    private long resourceVersion;
}
