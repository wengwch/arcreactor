package cn.veryai.arcreactor.actor.instance;

import java.time.Duration;
import java.util.Objects;

public record InstanceWorkflowSettings(Duration pollInitialDelay, Duration pollMaxDelay,
    int maxResourceReservationRetries, int maxConfirmationRetries, int maxProvisioningPollAttempts,
    int maxDeletionPollAttempts) {
  public InstanceWorkflowSettings {
    requirePositive(pollInitialDelay, "pollInitialDelay");
    requirePositive(pollMaxDelay, "pollMaxDelay");
    if (pollMaxDelay.compareTo(pollInitialDelay) < 0) {
      throw new IllegalArgumentException("pollMaxDelay must not be less than pollInitialDelay");
    }
    if (maxResourceReservationRetries < 0) {
      throw new IllegalArgumentException("maxResourceReservationRetries must not be negative");
    }
    if (maxConfirmationRetries < 0) {
      throw new IllegalArgumentException("maxConfirmationRetries must not be negative");
    }
    if (maxProvisioningPollAttempts <= 0) {
      throw new IllegalArgumentException("maxProvisioningPollAttempts must be positive");
    }
    if (maxDeletionPollAttempts <= 0) {
      throw new IllegalArgumentException("maxDeletionPollAttempts must be positive");
    }
  }

  public Duration pollDelay(long attempt) {
    Duration candidate;
    if (attempt <= 10) {
      candidate = pollInitialDelay;
    } else if (attempt <= 20) {
      candidate = pollInitialDelay.multipliedBy(3).dividedBy(2);
    } else if (attempt <= 30) {
      candidate = pollInitialDelay.multipliedBy(4).dividedBy(2);
    } else {
      candidate = pollMaxDelay;
    }
    return candidate.compareTo(pollMaxDelay) > 0 ? pollMaxDelay : candidate;
  }

  private static void requirePositive(Duration value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isNegative() || value.isZero())
      throw new IllegalArgumentException(name + " must be positive");
  }
}
