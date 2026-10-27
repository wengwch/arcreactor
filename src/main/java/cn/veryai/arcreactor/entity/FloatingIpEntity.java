package cn.veryai.arcreactor.entity;

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
    private byte[] portMask = new byte[0];
}
