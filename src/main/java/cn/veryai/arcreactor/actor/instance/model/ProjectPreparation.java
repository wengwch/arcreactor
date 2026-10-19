package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.time.Instant;

/** The project remains owned by UserActor when an instance workflow fails. */
public record ProjectPreparation(Instant deadline, String projectId) implements Serializable {}
