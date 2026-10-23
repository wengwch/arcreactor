package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "F")
@Data
public class GpuTypeEntity {
    private String id;
    private String name;
    private String vendorName;
    private String vendorCode;
    private String productName;
    private String productCode;
    private String description;
    /** 单卡显存容量，单位 GB。 */
    private int vram;
    private String pciPassthroughAlias;
}
