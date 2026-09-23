package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class SysRouterEntity {
  private String id;
  private String name;
  private String extNetId;
  private String sharedNetId;

  private String osRouterId;
  private String osProjectId;
  private String osExtNetId;
  private String osSharedNetId;
  private String regionId;
}
