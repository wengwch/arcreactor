package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.time.Instant;

/** One durable network operation; its internal ports, QoS and trunk are reconciled by marker. */
public record NetworkPreparation(Instant deadline, PreparedNetwork resources) implements Serializable {}
