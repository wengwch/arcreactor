package cn.veryai.arcreactor.web.params;

import cn.veryai.arcreactor.actor.instance.model.PortBandwidthLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateInstanceQosParam {
  @Valid
  private BandwidthLimitParam parentPort;
  @Valid
  private BandwidthLimitParam subPort;

  @AssertTrue(message = "at least one of parentPort or subPort is required")
  public boolean isPortSelected() {
    return parentPort != null || subPort != null;
  }

  @Data
  public static class BandwidthLimitParam {
    @NotNull
    @Min(0)
    private Integer maxKbps;
    @NotNull
    @Min(0)
    private Integer maxBurstKbits;

    @AssertTrue(message = "an unlimited port must have a zero burst size")
    public boolean isBurstValid() {
      return maxKbps == null || maxBurstKbits == null || maxKbps != 0 || maxBurstKbits == 0;
    }

    public PortBandwidthLimit toLimit() {
      return new PortBandwidthLimit(maxKbps, maxBurstKbits);
    }
  }
}
