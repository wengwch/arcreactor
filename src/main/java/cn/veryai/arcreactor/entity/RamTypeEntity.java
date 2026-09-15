package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** Hardware specification for a single memory module. */
@FieldNameConstants(innerTypeName = "F")
@Data
public class RamTypeEntity {
    private String id;
    private String name;
    private String vendorName;
    private String model;
    /** Memory generation, for example DDR4 or DDR5. */
    private String memoryType;
    /** Module type, for example UDIMM, RDIMM, or LRDIMM. */
    private String moduleType;
    private String description;

    /** Capacity in MiB. */
    private long capacityMb;
    /** Data transfer rate in millions of transfers per second. */
    private int speedMtps;
    private boolean eccSupported;
}
