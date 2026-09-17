package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.math.BigDecimal;

/** Reusable hardware specification for a hypervisor host. */
@FieldNameConstants(innerTypeName = "F")
@Data
public class HypervisorTypeEntity {
    private String id;
    private String name;
    private String description;

    private String vcpuSpec;
    private String gpuSpec;

    private String cpuType;
    private String ramType;
    private String diskType;
    private String gpuType;
    private int gpuTotal;

    private int vcpuUnit;
    private int ramUnit;
    private int diskUnit;
    private boolean enabled;
    private boolean privately;

    private BigDecimal originalHourlyPayPrice;
    private BigDecimal originalDailyPayPrice;
    private BigDecimal originalWeeklyPayPrice;
    private BigDecimal originalMonthlyPayPrice;

    private BigDecimal discountHourlyPayPrice;
    private BigDecimal discountDailyPayPrice;
    private BigDecimal discountWeeklyPayPrice;
    private BigDecimal discountMonthlyPayPrice;
}
