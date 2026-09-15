package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class FlavorEntity {

  private String id;
  private String name;
  private String description;
  private int vcpus;
  private int ram;
  private int localDisk;
  private int gpus;
  private String type;
  private int vram;
  private String gpuType;
  private String cpuSpec;
  private String osFlavorId;
  private String regionId;
  private boolean enabled = true;

}
