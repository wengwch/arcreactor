package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** Hardware specification for a single disk. */
@FieldNameConstants(innerTypeName = "F")
@Data
public class DiskTypeEntity {
    private String id;
    private String name;
    private String vendorName;
    private String model;
    /** Storage medium, for example HDD or SSD. */
    private String mediaType;
    /** Connection interface, for example SATA, SAS, or PCIe. */
    private String interfaceType;
    /** Command protocol, for example NVMe or SCSI. */
    private String protocol;
    private String formFactor;
    private String description;

    /** Capacity in GiB. */
    private long capacityGb;
    /** Rotational speed for HDDs; zero for solid-state disks. */
    private int rpm;
}
