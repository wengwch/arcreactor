package cn.veryai.arcreactor.actor.fip.projection;

import cn.veryai.arcreactor.actor.fip.FloatingIpActor;
import cn.veryai.arcreactor.actor.fip.event.FloatingIpEnabled;
import cn.veryai.arcreactor.actor.fip.event.FloatingIpEvent;
import cn.veryai.arcreactor.actor.fip.event.ForwardingCancelled;
import cn.veryai.arcreactor.actor.fip.event.ForwardingUpdated;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import cn.veryai.arcreactor.actor.fip.projection.mybatis.FloatingIpProjectionMapper;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

/** Projects the actor's complete port usage into the registered floating IP row. */
public final class FloatingIpProjectionHandler
    extends JdbcHandler<EventEnvelope<FloatingIpEvent>, JdbcProjectionSession> {

  @Override
  public void process(JdbcProjectionSession session, EventEnvelope<FloatingIpEvent> envelope) {
    FloatingIpEvent event = envelope.event();
    // Cancellation intent does not release ports; the cleanup events carry the resulting usage.
    if (event instanceof ForwardingCancelled) return;
    FloatingIpIdentity identity = event.identity();
    if (!(FloatingIpActor.ENTITY_TYPE + "|" + identity.entityId()).equals(envelope.persistenceId())) {
      throw new IllegalArgumentException("floating IP identity does not match persistence id: "
          + envelope.persistenceId());
    }
    byte[] portMask;
    if (event instanceof FloatingIpEnabled enabled) {
      portMask = enabled.usedPortMask();
    } else if (event instanceof ForwardingUpdated updated) {
      ForwardingClaim claim = updated.claim();
      if (!identity.regionId().equals(claim.spec().regionId())
          || !identity.floatingIpId().equals(claim.floatingIpId())) {
        throw new IllegalArgumentException("forwarding claim does not belong to floating IP");
      }
      portMask = updated.usedPortMask();
    } else {
      throw new IllegalArgumentException("unsupported floating IP event: " + event.getClass().getName());
    }

    FloatingIpProjectionMapper mapper = session.mapper(FloatingIpProjectionMapper.class);
    long version = envelope.sequenceNr();
    if (mapper.updatePortMask(identity, portMask, version) == 0) {
      Long projectedVersion = mapper.findResourceVersion(identity);
      if (projectedVersion == null || projectedVersion < version) {
        // Registration and actor persistence use separate transactions. Do not lose an early event.
        throw new IllegalStateException("floating IP row is not available for projection: " + identity.entityId());
      }
    }
  }
}
