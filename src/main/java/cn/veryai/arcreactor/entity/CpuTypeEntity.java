package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** Hardware specification for a single CPU package. */
@FieldNameConstants(innerTypeName = "F")
@Data
public class CpuTypeEntity {
    private String id;
    private String name;
}
