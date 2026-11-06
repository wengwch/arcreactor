package cn.veryai.arcreactor.actor.fip.projection;

import cn.veryai.arcreactor.actor.fip.FloatingIpActor;
import cn.veryai.arcreactor.actor.fip.event.FloatingIpDisabled;
import cn.veryai.arcreactor.actor.fip.event.FloatingIpEnabled;
import cn.veryai.arcreactor.actor.fip.event.FloatingIpEvent;
import cn.veryai.arcreactor.actor.fip.event.ForwardingDeleted;
import cn.veryai.arcreactor.actor.fip.event.ForwardingPortAllocated;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.projection.mybatis.FloatingIpProjectionMapper;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

/** Projects the actor's enabled state and complete port usage into the registered floating IP row. */
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
    byte[] portMask;
    boolean enabled;
    if (event instanceof FloatingIpEnabled activation) {
      enabled = true;
      portMask = activation.usedPortMask();
    } else if (event instanceof FloatingIpDisabled deactivation) {
      enabled = false;
      portMask = deactivation.usedPortMask();
    } else if (event instanceof ForwardingPortAllocated allocated) {
      enabled = allocated.enabled();
      portMask = allocated.usedPortMask();
    } else if (event instanceof ForwardingDeleted deleted) {
      enabled = deleted.enabled();
      portMask = deleted.usedPortMask();
    } else {
      // Requests, readiness, failures and outcome cleanup do not change port ownership.
      return;
    }

    FloatingIpProjectionMapper mapper = session.mapper(FloatingIpProjectionMapper.class);
    long version = envelope.sequenceNr();
    // Port changes also carry enabled, so cleanup cannot overwrite a disabled actor's status.
    if (mapper.updateState(identity, enabled, portMask, version) == 0) {
      Long projectedVersion = mapper.findResourceVersion(identity);
      if (projectedVersion == null || projectedVersion < version) {
        // Registration and actor persistence use separate transactions. Do not lose an early event.
        throw new IllegalStateException("floating IP row is not available for projection: " + identity.entityId());
      }
    }
  }
}
