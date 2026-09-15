package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class RegionEntity {
  private String id;
  private String name;
  private String desc;
  private String clusterId;
  private boolean enabled;
}
