package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class SaveGpuTypeParam {
    @NotBlank
    @Size(max = 255)
    private String name;
    @Size(max = 255)
    private String vendorName;
    @Size(max = 255)
    private String vendorCode;
    @Size(max = 255)
    private String productName;
    @Size(max = 255)
    private String productCode;
    private String description;
    @PositiveOrZero
    private long vram;
}
