package cn.veryai.arcreactor.actor.fip.model;

import cn.veryai.arcreactor.actor.fip.FloatingIpSettings;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.BitSet;
import java.util.List;

/** Fields needed from an existing OpenStack forwarding rule to synchronize port usage. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortForwardingSnapshot(@NotBlank String id,
    @JsonAlias("external_port") @Min(1) @Max(65535) int externalPort) implements Serializable {
  public PortForwardingSnapshot {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("forwarding id is required");
    if (externalPort < 1 || externalPort > 65535) throw new IllegalArgumentException("invalid external port");
  }

  /** Encodes the complete rule list within the actor's allocatable port range. */
  public static byte[] portMask(List<PortForwardingSnapshot> forwardings) {
    BitSet ports = new BitSet();
    for (PortForwardingSnapshot forwarding : forwardings) {
      int port = forwarding.externalPort();
      if (port >= FloatingIpSettings.FIRST_PORT && port <= FloatingIpSettings.LAST_PORT) {
        ports.set(port - FloatingIpSettings.FIRST_PORT);
      }
    }
    return ports.toByteArray();
  }
}
