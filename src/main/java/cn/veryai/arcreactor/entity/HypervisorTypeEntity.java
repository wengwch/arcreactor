package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** Reusable hardware specification for a hypervisor host. */
@FieldNameConstants(innerTypeName = "F")
@Data
public class HypervisorTypeEntity {
    private String id;
    private String name;
    private String description;

    private String cpuModel;
    private String cpuArchitecture;
    private int cpuSockets;
    /** Total number of logical CPUs across all sockets. */
    private int vcpus;

    /** Total memory capacity in MiB. */
    private long ram;
    /** Total local disk capacity in GiB. */
    private long localDisk;
    /** Local disk medium, for example HDD, SSD, or NVMe. */
    private String diskType;

    private String gpuType;
    /** Number of GPUs of the configured type. */
    private int gpus;
}
