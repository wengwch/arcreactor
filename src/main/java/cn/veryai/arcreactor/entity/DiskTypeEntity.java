package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** Hardware specification for a single disk. */
@FieldNameConstants(innerTypeName = "F")
@Data
public class DiskTypeEntity {
    private String id;
    private String name;
}
