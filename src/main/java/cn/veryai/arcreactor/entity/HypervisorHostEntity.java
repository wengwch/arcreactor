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

  /** 维护时同步容量和占用、配置 GPU 数量；启用后由 Actor Projection 维护资源账目。 */
  private int vcpus;

  /** 内存容量，单位 GB。 */
  private long ram;

  private long disk;
  private int gpus;
  /** 包含维护时同步的基础占用，以及后续确认分配的资源。 */
  private int usedVcpus;

  /** 已使用内存，单位 GB。 */
  private long usedRam;

  private long usedDisk;
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

  /** 创建中的预留资源与可用资源；可用量 = 总容量 - 已占用 - 预留。 */
  private String availabilityZone = "default";
  private int reservedVcpu;
  private int availableVcpu;
  private long reservedMemoryGb;
  private long availableMemoryGb;
  private long reservedDiskGb;
  private long availableDiskGb;
  private int reservedGpu;
  private int availableGpu;
  private long resourceVersion;
  private LocalDateTime updatedAt;

  public boolean isAvailable() {
    return Strings.CI.equals(this.status, "enabled") && Strings.CI.equals(this.state, "up");
  }
}
