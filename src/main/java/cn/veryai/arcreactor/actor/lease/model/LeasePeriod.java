package cn.veryai.arcreactor.actor.lease.model;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

public record LeasePeriod(long periodSeq, String orderId, String intentId, RentalPlan plan, long amount,
    Instant startsAt, Instant endsAt) implements Serializable {

  public long deductAt(Instant cutoff) {
    Instant bounded = cutoff.isBefore(startsAt) ? startsAt : cutoff.isAfter(endsAt) ? endsAt : cutoff;
    BigInteger total = nanos(Duration.between(startsAt, endsAt));
    // Preserve the previous rule: unused funds round down to cents; consumption is the remainder.
    long unusedAmount = BigInteger.valueOf(amount).multiply(nanos(Duration.between(bounded, endsAt)))
        .divide(total).longValueExact();
    return amount - unusedAmount;
  }

  private static BigInteger nanos(Duration duration) {
    return BigInteger.valueOf(duration.getSeconds()).multiply(BigInteger.valueOf(1_000_000_000L))
        .add(BigInteger.valueOf(duration.getNano()));
  }
}
