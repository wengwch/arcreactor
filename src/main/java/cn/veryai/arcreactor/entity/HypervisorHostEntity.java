package cn.veryai.arcreactor.entity;

import cn.veryai.arcreactor.enums.HypervisorStatus;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.Strings;

import java.time.LocalDateTime;

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

  /** 内存容量，单位 GB。 */
  private int ram;

  private int disk;
  private int gpus;
  private int usedVcpus;

  /** 已使用内存，单位 GB。 */
  private int usedRam;

  private int usedDisk;
  private int usedGpus;

  /** Actor 已确认分配的实例数量，由 Projection 更新。 */
  private int runningVms;

  private String status;
  private String state;
  private HypervisorStatus hypervisorStatus = HypervisorStatus.MAINTENANCE;

  /** 预计维护时间，未安排时为空。 */
  private LocalDateTime expectedMaintenanceTime;

  private boolean enabled;

  private String regionId;

  public boolean isAvailable() {
    return Strings.CI.equals(this.status, "enabled") && Strings.CI.equals(this.state, "up");
  }
}
