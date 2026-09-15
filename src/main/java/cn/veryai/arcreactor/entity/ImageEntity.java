package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class ImageEntity {
  private String id;
  private String name;
  private String distro;
  private String version;
  private boolean enabled;
  private String categoryId;
  private boolean gpuDriverInstalled;
  private String gpuDriver;
  private String osImageId;
  private String regionId;
}
