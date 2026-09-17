package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRegionParam extends SaveRegionParam {
    // Must match the region identifier in the OpenStack service catalog.
    @NotBlank
    @Size(max = 255)
    private String id;
}
