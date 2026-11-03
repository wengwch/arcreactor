package cn.veryai.arcreactor.actor.user.projection;

import cn.veryai.arcreactor.actor.user.UserActor;
import cn.veryai.arcreactor.actor.user.event.NetworkCreationRequested;
import cn.veryai.arcreactor.actor.user.event.NetworkBecameReady;
import cn.veryai.arcreactor.actor.user.event.NetworkCreationFailed;
import cn.veryai.arcreactor.actor.user.event.ProjectBecameReady;
import cn.veryai.arcreactor.actor.user.event.ShareUpdated;
import cn.veryai.arcreactor.actor.user.event.UserEvent;
import cn.veryai.arcreactor.actor.user.model.UserNetworkState;
import cn.veryai.arcreactor.actor.user.projection.mybatis.UserProjectionMapper;
import cn.veryai.arcreactor.entity.UserClusterProjectEntity;
import java.sql.Timestamp;
import java.time.Instant;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

/** Projects ready cluster projects, share snapshots and network lifecycle events. */
public final class UserProjectionHandler
    extends JdbcHandler<EventEnvelope<UserEvent>, JdbcProjectionSession> {

  @Override
  public void process(JdbcProjectionSession session, EventEnvelope<UserEvent> envelope) {
    UserEvent event = envelope.event();
    UserProjectionMapper mapper = session.mapper(UserProjectionMapper.class);
    String userId = entityId(envelope.persistenceId());
    long version = envelope.sequenceNr();
    Timestamp timestamp = Timestamp.from(Instant.ofEpochMilli(envelope.timestamp()));

    if (event instanceof ProjectBecameReady ready) {
      if (ready.clusterId() == null || ready.clusterId().isBlank()
          || ready.projectId() == null || ready.projectId().isBlank()) {
        throw new IllegalArgumentException("ready project must have cluster and project IDs");
      }
      UserClusterProjectEntity entity = new UserClusterProjectEntity();
      entity.setUserId(userId);
      entity.setClusterId(ready.clusterId());
      entity.setProjectId(ready.projectId());
      mapper.upsertProject(entity);
    } else if (event instanceof ShareUpdated updated) {
      mapper.upsertShare(userId, updated.share(), version, timestamp);
    } else if (event instanceof NetworkCreationRequested requested) {
      UserNetworkState network = UserNetworkState.requested(requested.spec(), requested.projectId());
      // Router resources share the network workflow; both rows and the offset commit together.
      mapper.upsertNetwork(userId, network, version, timestamp);
      mapper.upsertRouter(userId, network, version, timestamp);
    } else if (event instanceof NetworkBecameReady ready) {
      mapper.markNetworkReady(userId, ready, version, timestamp);
      mapper.markRouterReady(userId, ready, version, timestamp);
    } else if (event instanceof NetworkCreationFailed failed) {
      mapper.markNetworkFailed(userId, failed, version, timestamp);
      mapper.markRouterFailed(userId, failed, version, timestamp);
    }
  }

  static String entityId(String persistenceId) {
    String prefix = UserActor.ENTITY_TYPE + "|";
    if (persistenceId == null || !persistenceId.startsWith(prefix)
        || persistenceId.substring(prefix.length()).isBlank()) {
      throw new IllegalArgumentException("unexpected user persistence id: " + persistenceId);
    }
    return persistenceId.substring(prefix.length());
  }
}
