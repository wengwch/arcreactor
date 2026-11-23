package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.time.Instant;

/** Persisted QoS request with its required expiry time and optional expiry reset intent. */
public record InstanceQosUpdate(String operationId, Phase phase, PortBandwidthLimit parentPort,
    PortBandwidthLimit subPort, String failure, Instant expiresAt,
    PortBandwidthLimit expiryParentPort, PortBandwidthLimit expirySubPort) implements Serializable {
  public enum Phase { UPDATING, READY, FAILED, EXPIRING, EXPIRY_FAILED, EXPIRED, CANCELLED }

  public InstanceQosUpdate completed() {
    return withPhase(phase == Phase.EXPIRING ? Phase.EXPIRED : Phase.READY, null);
  }

  public InstanceQosUpdate failed(String detail) {
    return withPhase(phase == Phase.EXPIRING ? Phase.EXPIRY_FAILED : Phase.FAILED, detail);
  }

  public InstanceQosUpdate expiring(PortBandwidthLimit parent, PortBandwidthLimit sub) {
    return new InstanceQosUpdate(operationId, Phase.EXPIRING, parentPort, subPort, null,
        expiresAt, parent, sub);
  }

  private InstanceQosUpdate withPhase(Phase nextPhase, String detail) {
    return new InstanceQosUpdate(operationId, nextPhase, parentPort, subPort, detail,
        expiresAt, expiryParentPort, expirySubPort);
  }
}

