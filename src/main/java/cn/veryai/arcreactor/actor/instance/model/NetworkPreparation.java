package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;

/** Network resources, empty until the parent port, subport and trunk are all prepared. */
public record NetworkPreparation(PreparedNetwork resources) implements Serializable {}
