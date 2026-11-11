package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class SaveOpenstackClusterParam {
    @NotBlank
    @Size(max = 255)
    private String name;
    private String description;
    @NotBlank
    @Size(max = 255)
    private String adminUsername;
    @NotBlank
    @Size(max = 1024)
    @ToString.Exclude
    private String adminPassword;
    @NotBlank
    @Size(max = 255)
    private String osAdminDefaultProjectId;
    @NotBlank
    @Size(max = 255)
    private String osAdminDefaultDomainId;
    @NotBlank
    @Size(max = 255)
    private String osAdminDefaultRegion;
    @NotBlank
    @Size(max = 1024)
    private String authEndpoint;
    @Size(max = 255)
    private String osMemberRoleId;
    @Size(max = 255)
    private String osAdminRoleId;
}
