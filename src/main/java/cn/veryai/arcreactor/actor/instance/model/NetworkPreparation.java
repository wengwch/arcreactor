package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;

/** Accumulated resources for the durable parent port, subport and trunk stages. */
public record NetworkPreparation(PreparedNetwork resources) implements Serializable {}
