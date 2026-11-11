package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;

/** Dedicated port QoS resources; unlimited policies have no ingress or egress bandwidth rules. */
public record PreparedPortQos(String osPolicyId, String osIngressRuleId, String osEgressRuleId) implements Serializable {}
