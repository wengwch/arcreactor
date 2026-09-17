package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class SysRouterEntity {
  private String id;
  private String name;
  private String extNetId;

  private String osRouterId;
  private String osProjectId;
  private String osExtNetId;
  private String osSharedNetSubnetId;
  private String osSharedNetPortId;
  private String regionId;
  private String zoneId;
}
