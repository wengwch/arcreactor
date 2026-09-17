package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveCpuTypeParam {
    @NotBlank
    @Size(max = 255)
    private String name;
}
