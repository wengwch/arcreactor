package cn.veryai.arcreactor.web.params;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUserForwardingParam {
  @NotNull
  @Min(1)
  @Max(65535)
  private Integer internalPort;
}
