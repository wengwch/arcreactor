package cn.veryai.arcreactor.entity;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/** Hardware specification for a single memory module. */
@FieldNameConstants(innerTypeName = "F")
@Data
public class RamTypeEntity {
    private String id;
    private String name;
}
