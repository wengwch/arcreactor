package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class UserClusterProjectEntity {
  private String userId;
  private String clusterId;
  private String projectId;
}
