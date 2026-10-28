package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;

/** Symmetric ingress/egress limit: rate in kilobits/second, burst size in kilobits. */
public record PortBandwidthLimit(int maxKbps, int maxBurstKbits) implements Serializable {
  public static final PortBandwidthLimit UNLIMITED = new PortBandwidthLimit(0, 0);

  public PortBandwidthLimit {
    if (maxKbps < 0 || maxBurstKbits < 0) {
      throw new IllegalArgumentException("port bandwidth limit values must not be negative");
    }
    if (maxKbps == 0 && maxBurstKbits != 0) {
      throw new IllegalArgumentException("an unlimited port must have a zero burst size");
    }
  }

  /** A zero rate keeps the dedicated QoS policy without bandwidth limit rules. */
  public boolean limited() {
    return maxKbps > 0;
  }
}
