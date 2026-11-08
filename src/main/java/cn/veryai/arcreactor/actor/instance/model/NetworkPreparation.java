package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.time.Instant;

/** Shared deadline and accumulated resources for the durable parent port, subport and trunk stages. */
public record NetworkPreparation(Instant deadline, PreparedNetwork resources) implements Serializable {}
