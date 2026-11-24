package cn.veryai.arcreactor.actor.lease;

import java.time.Duration;

public record LeaseSettings(Duration retryInterval) {}
