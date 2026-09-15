package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.Strings;

@FieldNameConstants(innerTypeName = "F")
@Data
public class HypervisorHostEntity {
  private String id;
  private String name;
  private String hostname;
  private String ip;
  private String osHypervisorId;
  private String description;
  private HypervisorTypeEntity type;
  private GpuTypeEntity gpuTypeEntity;

  private int vcpus;
  private int ram;
  private int localDisk;
  private int gpus;
  private int usedVcpus;
  private int usedRam;
  private int usedLocalDisk;
  private int usedGpus;

  private String status;
  private String state;
  private boolean enabled;
  private boolean privately;

  private String regionId;
  private String regionName;
  private String zoneId;
  private String zoneName;

  public boolean isAvailable() {
    return privately
        || (enabled
            && Strings.CI.equals(this.status, "enabled")
            && Strings.CI.equals(this.state, "up"));
  }

  public int getAvailableVcpus() {
    return vcpus - usedVcpus;
  }

  public int getAvailableRam() {
    return ram - usedRam;
  }

  public int getAvailableLocalDisk() {
    return localDisk - usedLocalDisk;
  }

  public int getAvailableGpus() {
    return gpus - usedGpus;
  }

  public void acquireVcpus(int num) {
    this.usedVcpus += num;
  }

  public void releaseVcpus(int num) {
    this.usedVcpus -= num;
  }

  public void acquireRam(int num) {
    this.usedRam += num;
  }

  public void releaseRam(int num) {
    this.usedRam -= num;
  }

  public void acquireLocalDisk(int num) {
    this.usedLocalDisk += num;
  }

  public void releaseLocalDisk(int num) {
    this.usedLocalDisk -= num;
  }

  public void acquireGpus(int num) {
    this.usedGpus += num;
  }

  public void releaseGpus(int num) {
    this.usedGpus -= num;
  }
}
