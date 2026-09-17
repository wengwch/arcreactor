package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaveHypervisorTypeParam {
    @NotBlank
    @Size(max = 255)
    private String name;
    private String description;
    @Size(max = 255)
    private String vcpuSpec;
    @Size(max = 255)
    private String gpuSpec;
    @Size(max = 255)
    private String cpuType;
    @Size(max = 255)
    private String ramType;
    @Size(max = 255)
    private String diskType;
    @Size(max = 255)
    private String gpuType;
    @PositiveOrZero
    private int gpuTotal;
    @PositiveOrZero
    private int vcpuUnit;
    @PositiveOrZero
    private int ramUnit;
    @PositiveOrZero
    private int diskUnit;
    @NotNull
    private Boolean enabled = true;
    @NotNull
    private Boolean privately = false;
    @DecimalMin("0")
    @Digits(integer = 13, fraction = 6)
    private BigDecimal originalHourlyPayPrice;
    @DecimalMin("0")
    @Digits(integer = 13, fraction = 6)
    private BigDecimal originalDailyPayPrice;
    @DecimalMin("0")
    @Digits(integer = 13, fraction = 6)
    private BigDecimal originalWeeklyPayPrice;
    @DecimalMin("0")
    @Digits(integer = 13, fraction = 6)
    private BigDecimal originalMonthlyPayPrice;
    @DecimalMin("0")
    @Digits(integer = 13, fraction = 6)
    private BigDecimal discountHourlyPayPrice;
    @DecimalMin("0")
    @Digits(integer = 13, fraction = 6)
    private BigDecimal discountDailyPayPrice;
    @DecimalMin("0")
    @Digits(integer = 13, fraction = 6)
    private BigDecimal discountWeeklyPayPrice;
    @DecimalMin("0")
    @Digits(integer = 13, fraction = 6)
    private BigDecimal discountMonthlyPayPrice;
}
