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
  /** 内存容量，单位 GB。 */
  private int ram;
  private int disk;
  private int gpus;
  /** 显存总容量，单位 GB。 */
  private int vram;

  private String hypervisorType;
  private String cpuType;
  private String ramType;
  private String diskType;
  private String gpuType;

  private String regionId;
  private boolean enabled = true;
  private String osFlavorId;

}
