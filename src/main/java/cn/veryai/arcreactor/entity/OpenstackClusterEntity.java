package cn.veryai.arcreactor.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class OpenstackClusterEntity {
    private String id;
    private String name;
    private String description;
    private String adminUsername;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String adminPassword;
    private String osAdminDefaultProjectId;
    private String osAdminDefaultDomainId;
    private String osAdminDefaultRegion;
    private String authEndpoint;
    private String osMemberRoleId;
    private String osAdminRoleId;

}
