package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class OpenstackClusterEntity {
    private String id;
    private String name;
    private String description;
    private String adminUsername;
    private String adminPassword;
    private String adminDefaultProjectId;
    private String adminDefaultDomainId;
    private String adminDefaultRegion;
    private String authEndpoint;
    private String memberRoleId;
    private String adminRoleId;

}
