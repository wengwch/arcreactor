package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;

public record InstanceQosUpdate(String operationId, Phase phase, PortBandwidthLimit parentPort,
    PortBandwidthLimit subPort, String failure) implements Serializable {
  public enum Phase { UPDATING, READY, FAILED }
  public InstanceQosUpdate completed() {
    return new InstanceQosUpdate(operationId, Phase.READY, parentPort, subPort, null);
  }
  public InstanceQosUpdate failed(String detail) {
    return new InstanceQosUpdate(operationId, Phase.FAILED, parentPort, subPort, detail);
  }
}

