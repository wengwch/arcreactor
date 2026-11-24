package cn.veryai.arcreactor.actor.order;

import java.time.Duration;

public record OrderSettings(Duration retryInterval, Duration requestTimeout) {}
