package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class HypervisorHostEntity {
  private String id;
  private String name;
  private String description;
  private String hostname;
  private String ip;
  private String osHypervisorId;
  private String hypervisorType;
  private String cpuType;
  private String ramType;
  private String diskType;
  private String gpuType;

  private int vcpus;
  private int ram;
  private int disk;
  private int gpus;
  private int usedVcpus;
  private int usedRam;
  private int usedDisk;
  private int usedGpus;

  private String status;
  private String state;
  private boolean enabled;

  private String regionId;

}
