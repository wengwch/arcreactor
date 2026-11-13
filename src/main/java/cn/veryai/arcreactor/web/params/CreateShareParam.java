package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateShareParam {
    @NotBlank
    @Size(max = 255)
    private String regionId;
    /** Capacity in GiB. */
    @Positive
    private int size;
    /** Manila backend share type; the application share kind is always USER. */
    @NotBlank
    @Size(max = 255)
    private String shareType;
}
