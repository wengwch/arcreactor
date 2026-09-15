package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class ImageCategoryEntity {
  private String id;
  private String name;
  private String distro;
  private String version;
  private boolean enabled;
}
