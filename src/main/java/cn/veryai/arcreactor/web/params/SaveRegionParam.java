package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveRegionParam {
    @NotBlank
    @Size(max = 255)
    private String name;
    private String desc;
    @NotBlank
    @Size(max = 255)
    private String clusterId;
    @NotNull
    private Boolean enabled = true;
}
