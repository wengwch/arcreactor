package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class UserRegionProjectEntity {
  private String userId;
  private String regionId;
  private String projectId;
}
