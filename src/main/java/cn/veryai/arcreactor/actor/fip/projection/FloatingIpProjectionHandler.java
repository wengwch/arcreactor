package cn.veryai.arcreactor.actor.fip.projection;

import cn.veryai.arcreactor.actor.fip.FloatingIpActor;
import cn.veryai.arcreactor.actor.fip.event.FloatingIpDestroyed;
import cn.veryai.arcreactor.actor.fip.event.FloatingIpDestructionFailed;
import cn.veryai.arcreactor.actor.fip.event.FloatingIpDestructionStarted;
import cn.veryai.arcreactor.actor.fip.event.FloatingIpDisabled;
import cn.veryai.arcreactor.actor.fip.event.FloatingIpEnabled;
import cn.veryai.arcreactor.actor.fip.event.FloatingIpEvent;
import cn.veryai.arcreactor.actor.fip.event.ForwardingBecameReady;
import cn.veryai.arcreactor.actor.fip.event.ForwardingCreationFailed;
import cn.veryai.arcreactor.actor.fip.event.ForwardingCreationRequested;
import cn.veryai.arcreactor.actor.fip.event.ForwardingDeleted;
import cn.veryai.arcreactor.actor.fip.event.ForwardingDeletionFailed;
import cn.veryai.arcreactor.actor.fip.event.ForwardingDeletionStarted;
import cn.veryai.arcreactor.actor.fip.event.ForwardingPortAllocated;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpState.Status;
import cn.veryai.arcreactor.actor.fip.projection.mybatis.FloatingIpProjectionMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

/** Projects floating IP port usage and forwarding lifecycles in the same transaction. */
public final class FloatingIpProjectionHandler
    extends JdbcHandler<EventEnvelope<FloatingIpEvent>, JdbcProjectionSession> {

  @Override
  public void process(JdbcProjectionSession session, EventEnvelope<FloatingIpEvent> envelope) {
    FloatingIpEvent event = envelope.event();
    FloatingIpIdentity identity = event.identity();
    if (!(FloatingIpActor.ENTITY_TYPE + "|" + identity.entityId()).equals(envelope.persistenceId())) {
      throw new IllegalArgumentException("floating IP identity does not match persistence id: "
          + envelope.persistenceId());
    }
    FloatingIpProjectionMapper mapper = session.mapper(FloatingIpProjectionMapper.class);
    long version = envelope.sequenceNr();
    Timestamp timestamp = Timestamp.from(Instant.ofEpochMilli(envelope.timestamp()));
    projectForwarding(mapper, event, version, timestamp);

    byte[] portMask = null;
    Status status;
    if (event instanceof FloatingIpEnabled activation) {
      status = Status.ENABLED;
      portMask = activation.usedPortMask();
    } else if (event instanceof FloatingIpDisabled deactivation) {
      status = Status.DISABLED;
      portMask = deactivation.usedPortMask();
    } else if (event instanceof FloatingIpDestructionStarted) {
      status = Status.DESTROYING;
    } else if (event instanceof FloatingIpDestructionFailed) {
      status = Status.DISABLED;
    } else if (event instanceof FloatingIpDestroyed) {
      status = Status.DESTROYED;
      portMask = new byte[0];
    } else if (event instanceof ForwardingPortAllocated allocated) {
      status = allocated.status();
      portMask = allocated.usedPortMask();
    } else if (event instanceof ForwardingDeleted deleted) {
      status = deleted.status();
      portMask = deleted.usedPortMask();
    } else {
      // These events only change forwarding history or retained actor outcomes.
      return;
    }

    // Status-only events preserve the bitmap; port changes carry the status at the time of the event.
    String encodedPortMask = portMask == null ? null : Base64.getEncoder().encodeToString(portMask);
    mapper.updateState(identity, status, encodedPortMask, version);
  }

  private static void projectForwarding(FloatingIpProjectionMapper mapper, FloatingIpEvent event,
      long version, Timestamp timestamp) {
    String claimId;
    int written;
    if (event instanceof ForwardingCreationRequested created) {
      claimId = created.claimId();
      written = mapper.upsertForwardingCreated(created, version, timestamp);
    } else if (event instanceof ForwardingPortAllocated allocated) {
      claimId = allocated.claimId();
      written = mapper.updateForwardingAllocated(allocated, version, timestamp);
    } else if (event instanceof ForwardingBecameReady ready) {
      claimId = ready.claimId();
      written = mapper.updateForwardingReady(ready, version, timestamp);
    } else if (event instanceof ForwardingCreationFailed failed) {
      claimId = failed.claimId();
      written = mapper.updateForwardingCreationFailed(failed, version, timestamp);
    } else if (event instanceof ForwardingDeletionStarted started) {
      claimId = started.claimId();
      written = mapper.updateForwardingDeletionStarted(started, version, timestamp);
    } else if (event instanceof ForwardingDeletionFailed failed) {
      claimId = failed.claimId();
      written = mapper.updateForwardingDeletionFailed(failed, version, timestamp);
    } else if (event instanceof ForwardingDeleted deleted) {
      claimId = deleted.claimId();
      written = mapper.upsertForwardingDeleted(deleted, version, timestamp, Timestamp.from(deleted.deletedAt()));
    } else {
      // Actor outcome retention does not remove forwarding history from the read model.
      return;
    }
    if (written == 0) {
      Long projectedVersion = mapper.findForwardingVersion(event.identity(), claimId);
      if (projectedVersion == null || projectedVersion < version) {
        throw new IllegalStateException("port forwarding creation record is missing; replay the projection from creation: "
            + event.identity().entityId() + "/" + claimId);
      }
    }
  }
}
