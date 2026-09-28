package cn.veryai.arcreactor.actor.instance;

import java.time.Duration;
import java.util.Objects;

public record InstanceWorkflowSettings(
        Duration reservationTtl,
        Duration provisioningTimeout,
        Duration pollInitialDelay,
        Duration pollMaxDelay) {
    public InstanceWorkflowSettings {
        requirePositive(reservationTtl, "reservationTtl");
        requirePositive(provisioningTimeout, "provisioningTimeout");
        requirePositive(pollInitialDelay, "pollInitialDelay");
        requirePositive(pollMaxDelay, "pollMaxDelay");
        if (pollMaxDelay.compareTo(pollInitialDelay) < 0) {
            throw new IllegalArgumentException("pollMaxDelay must not be less than pollInitialDelay");
        }
    }

    public Duration pollDelay(long attempt) {
        Duration candidate = switch ((int) Math.min(attempt, 4)) {
            case 0, 1 -> pollInitialDelay;
            case 2 -> pollInitialDelay.multipliedBy(3).dividedBy(2);
            case 3 -> pollInitialDelay.multipliedBy(5).dividedBy(2);
            default -> pollMaxDelay;
        };
        return candidate.compareTo(pollMaxDelay) > 0 ? pollMaxDelay : candidate;
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) throw new IllegalArgumentException(name + " must be positive");
    }
}
