package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;

/** Dedicated port QoS resources and requested bandwidth settings. */
public record PreparedPortQos(String osPolicyId, String osIngressRuleId, String osEgressRuleId,
    PortBandwidthLimit bandwidthLimit) implements Serializable {}
