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
  /** 可选的宿主机 hostname，指定后仅在匹配的主机上调度。 */
  @Size(max = 255)
  private String hostname;
  @NotBlank
  @Size(max = 255)
  private String regionId;
  @NotBlank
  @Size(max = 255)
  private String userId;
}
