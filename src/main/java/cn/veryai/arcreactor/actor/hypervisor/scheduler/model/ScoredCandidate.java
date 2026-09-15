package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import java.io.Serializable;

public record ScoredCandidate(HypervisorCandidate candidate, double score) implements Serializable {}
