package cn.veryai.arcreactor.actor.user.projection;

import cn.veryai.arcreactor.actor.user.UserActor;
import cn.veryai.arcreactor.actor.user.event.NetworkCreationRequested;
import cn.veryai.arcreactor.actor.user.event.NetworkBecameReady;
import cn.veryai.arcreactor.actor.user.event.NetworkCreationFailed;
import cn.veryai.arcreactor.actor.user.event.ProjectBecameReady;
import cn.veryai.arcreactor.actor.user.event.ProjectCreationFailed;
import cn.veryai.arcreactor.actor.user.event.ProjectCreationRequested;
import cn.veryai.arcreactor.actor.user.event.ShareUpdated;
import cn.veryai.arcreactor.actor.user.event.UserEvent;
import cn.veryai.arcreactor.actor.user.model.UserNetworkState;
import cn.veryai.arcreactor.actor.user.model.UserProjectState;
import cn.veryai.arcreactor.actor.user.projection.mybatis.UserProjectionMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

/** Projects every project, share and network state with journal sequence number guards. */
public final class UserProjectionHandler
    extends JdbcHandler<EventEnvelope<UserEvent>, JdbcProjectionSession> {

  @Override
  public void process(JdbcProjectionSession session, EventEnvelope<UserEvent> envelope) {
    UserEvent event = envelope.event();
    UserProjectionMapper mapper = session.mapper(UserProjectionMapper.class);
    String userId = entityId(envelope.persistenceId());
    long version = envelope.sequenceNr();
    Timestamp timestamp = Timestamp.from(Instant.ofEpochMilli(envelope.timestamp()));

    if (event instanceof ProjectCreationRequested(String clusterId, String name)) {
      mapper.upsertProject(userId, UserProjectState.requested(clusterId, name),
          version, timestamp);
    } else if (event instanceof ProjectBecameReady(String clusterId, String name, String projectId)) {
      if (clusterId == null || clusterId.isBlank()
          || projectId == null || projectId.isBlank()) {
        throw new IllegalArgumentException("ready project must have cluster and project IDs");
      }
      mapper.upsertProject(userId, UserProjectState.requested(clusterId, name).ready(projectId),
          version, timestamp);
    } else if (event instanceof ProjectCreationFailed(String clusterId, String name, String failure)) {
      mapper.upsertProject(userId, UserProjectState.requested(clusterId, name).failed(failure),
          version, timestamp);
    } else if (event instanceof ShareUpdated updated) {
      mapper.upsertShare(userId, updated.share(), version, timestamp);
    } else if (event instanceof NetworkCreationRequested requested) {
      upsertNetwork(mapper, userId, UserNetworkState.requested(requested.spec(), requested.projectId()),
          version, timestamp);
    } else if (event instanceof NetworkBecameReady ready) {
      upsertNetwork(mapper, userId, UserNetworkState.requested(ready.spec(), ready.projectId()).ready(ready.resources()),
          version, timestamp);
    } else if (event instanceof NetworkCreationFailed failed) {
      upsertNetwork(mapper, userId, UserNetworkState.requested(failed.spec(), failed.projectId()).failed(failed.failure()),
          version, timestamp);
    }
  }

  private static void upsertNetwork(UserProjectionMapper mapper, String userId, UserNetworkState network,
      long version, Timestamp timestamp) {
    // Both rows and the offset commit together, even when the terminal event arrives first.
    mapper.upsertNetwork(userId, network, version, timestamp);
    mapper.upsertRouter(userId, network, version, timestamp);
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
