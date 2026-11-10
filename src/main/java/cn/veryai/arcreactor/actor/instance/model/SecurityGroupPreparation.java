package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;

/** The instance owns this group; a missing ID is recovered using the creation operation marker. */
public record SecurityGroupPreparation(String securityGroupId) implements Serializable {}
