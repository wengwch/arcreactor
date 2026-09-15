package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** Hardware specification for a single CPU package. */
@FieldNameConstants(innerTypeName = "F")
@Data
public class CpuTypeEntity {
    private String id;
    private String name;
    private String vendorName;
    private String model;
    private String architecture;
    private String socketType;
    private String description;

    private int cores;
    private int threads;
    private int baseFrequencyMhz;
    private int maxFrequencyMhz;
    /** Thermal design power in watts. */
    private int tdpWatts;
}
