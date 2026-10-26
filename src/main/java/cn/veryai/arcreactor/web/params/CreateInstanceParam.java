package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateInstanceParam {
  @NotBlank
  @Size(max = 255)
  private String name;
  @Size(max = 255)
  private String description;
  @NotBlank
  @Size(max = 255)
  private String flavorId;
  @NotBlank
  @Size(max = 255)
  private String imageId;
  @NotBlank
  @Size(max = 255)
  private String hypervisorType;
  @NotBlank
  @Size(max = 255)
  private String regionId;
  @NotBlank
  @Size(max = 255)
  private String userId;
}
