package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RebuildInstanceParam {
  /** Local image ID, resolved to an OpenStack image in the instance's region. */
  @NotBlank
  @Size(max = 255)
  private String imageId;
}
