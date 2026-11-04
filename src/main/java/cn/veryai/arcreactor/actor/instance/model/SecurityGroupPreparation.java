package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.time.Instant;

/** The instance owns this group; a missing ID is recovered using the creation operation marker. */
public record SecurityGroupPreparation(Instant deadline, String securityGroupId) implements Serializable {}
