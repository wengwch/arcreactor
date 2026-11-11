package cn.veryai.arcreactor.actor.fip.model;

import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim.Phase;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Compact result for a claim that no longer owns any remote resources. */
public record ForwardingOutcome(String specFingerprint, Phase phase,
    String failure, Instant completedAt) implements Serializable {
  public ForwardingOutcome {
    if (phase != Phase.FAILED && phase != Phase.DELETED) {
      throw new IllegalArgumentException("only failed or deleted forwarding outcomes can be retained");
    }
    Objects.requireNonNull(completedAt, "completedAt");
  }

  public static ForwardingOutcome from(ForwardingClaim claim, Instant completedAt) {
    if (claim.ownsPort()
        || claim.phase() != Phase.FAILED && claim.phase() != Phase.DELETED) {
      throw new IllegalArgumentException("only terminal forwardings without retained resources can be compacted");
    }
    return new ForwardingOutcome(fingerprint(claim.spec()), claim.phase(), claim.failure(), completedAt);
  }

  /** Records deletion before any create was accepted, blocking delayed creates during retention. */
  public static ForwardingOutcome tombstone(Instant deletedAt) {
    return new ForwardingOutcome(null, Phase.DELETED, null, deletedAt);
  }

  public boolean matches(ForwardingSpec spec) {
    return specFingerprint != null && specFingerprint.equals(fingerprint(spec));
  }

  public ForwardingOutcome deleted(Instant deletedAt) {
    return phase == Phase.DELETED ? this
        : new ForwardingOutcome(specFingerprint, Phase.DELETED, failure, deletedAt);
  }

  private static String fingerprint(ForwardingSpec spec) {
    StringBuilder value = new StringBuilder();
    for (String field : List.of(spec.instanceId(), spec.userId(), spec.regionId(), spec.osProjectId(),
        spec.osInternalPortId(), spec.fixedIp(), Integer.toString(spec.internalPort()))) {
      value.append(field.length()).append(':').append(field);
    }
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }
}
