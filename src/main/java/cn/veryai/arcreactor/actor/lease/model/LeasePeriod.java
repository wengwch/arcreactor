package cn.veryai.arcreactor.actor.lease.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

public record LeasePeriod(
    long periodSeq,
    String orderId,
    RentalPlan plan,
    BigDecimal amount,
    Instant startsAt,
    Instant endsAt)
    implements Serializable {

  public BigDecimal deductAt(Instant cutoff) {
    Instant bounded =
        cutoff.isBefore(startsAt.plus(Duration.ofMinutes(5)))
            ? startsAt
            : cutoff.isAfter(endsAt.minus(Duration.ofMinutes(5))) ? endsAt : cutoff;
    long totalMinutes = Duration.between(startsAt, endsAt).toMinutes();
    long usedMinutes = Duration.between(startsAt, bounded).toMinutes();
    return amount
        .multiply(BigDecimal.valueOf(usedMinutes))
        .divide(BigDecimal.valueOf(totalMinutes), 2, RoundingMode.HALF_UP);
  }
}
