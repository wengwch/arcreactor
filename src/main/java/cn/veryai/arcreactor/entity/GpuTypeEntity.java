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
    private String productCode;
    private String description;
    private long vram;
}
