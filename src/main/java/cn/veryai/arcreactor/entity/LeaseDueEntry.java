package cn.veryai.arcreactor.entity;

import java.time.Instant;

/** A due lease projection row; both fields also form its scan cursor. */
public record LeaseDueEntry(String leaseId, Instant nextActionAt) {}
