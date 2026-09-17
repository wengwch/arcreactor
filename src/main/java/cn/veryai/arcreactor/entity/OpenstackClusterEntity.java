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
    private String adminDefaultProjectId;
    private String adminDefaultDomainId;
    private String adminDefaultRegion;
    private String authEndpoint;
    private String memberRoleId;
    private String adminRoleId;

}
