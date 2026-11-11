package cn.veryai.arcreactor.actor.user.projection;

import cn.veryai.arcreactor.actor.user.UserActor;
import cn.veryai.arcreactor.actor.user.event.NetworkCreationRequested;
import cn.veryai.arcreactor.actor.user.event.NetworkBecameReady;
import cn.veryai.arcreactor.actor.user.event.NetworkCreationFailed;
import cn.veryai.arcreactor.actor.user.event.ProjectBecameReady;
import cn.veryai.arcreactor.actor.user.event.ProjectCreationFailed;
import cn.veryai.arcreactor.actor.user.event.ProjectCreationRequested;
import cn.veryai.arcreactor.actor.user.event.ShareCreationRequested;
import cn.veryai.arcreactor.actor.user.event.ShareCreated;
import cn.veryai.arcreactor.actor.user.event.ShareExportBecameReady;
import cn.veryai.arcreactor.actor.user.event.ShareAccessCreated;
import cn.veryai.arcreactor.actor.user.event.ShareBecameReady;
import cn.veryai.arcreactor.actor.user.event.ShareCreationFailed;
import cn.veryai.arcreactor.actor.user.event.UserEvent;
import cn.veryai.arcreactor.actor.user.model.UserNetworkState;
import cn.veryai.arcreactor.actor.user.model.UserProjectState;
import cn.veryai.arcreactor.actor.user.model.UserShareState;
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

    if (event instanceof ProjectCreationRequested(String clusterId, String name, Instant deadline)) {
      mapper.upsertProject(userId, UserProjectState.requested(clusterId, name, deadline),
          version, timestamp);
    } else if (event instanceof ProjectBecameReady(String clusterId, String name, String osProjectId)) {
      if (clusterId == null || clusterId.isBlank()
          || osProjectId == null || osProjectId.isBlank()) {
        throw new IllegalArgumentException("ready project must have cluster and project IDs");
      }
      mapper.upsertProject(userId, UserProjectState.requested(clusterId, name, null).ready(osProjectId),
          version, timestamp);
    } else if (event instanceof ProjectCreationFailed(String clusterId, String name, String failure)) {
      mapper.upsertProject(userId, UserProjectState.requested(clusterId, name, null).failed(failure),
          version, timestamp);
    } else if (event instanceof ShareCreationRequested requested) {
      mapper.upsertShare(userId, UserShareState.requested(requested.shareId(), requested.spec(), requested.deadline()),
          version, timestamp);
    } else if (event instanceof ShareCreated created) {
      mapper.upsertShare(userId, UserShareState.requested(created.shareId(), created.spec(), null).created(created.osShareId()),
          version, timestamp);
    } else if (event instanceof ShareExportBecameReady ready) {
      mapper.upsertShare(userId, UserShareState.requested(ready.shareId(), ready.spec(), null).created(ready.osShareId())
          .exportReady(ready.osExportLocationId(), ready.exportLocationPath()), version, timestamp);
    } else if (event instanceof ShareAccessCreated created) {
      mapper.upsertShare(userId, UserShareState.requested(created.shareId(), created.spec(), null).created(created.osShareId())
          .exportReady(created.osExportLocationId(), created.exportLocationPath()).accessCreated(created.osGrantAccessId()),
          version, timestamp);
    } else if (event instanceof ShareBecameReady ready) {
      mapper.upsertShare(userId, UserShareState.requested(ready.shareId(), ready.spec(), null).created(ready.osShareId())
          .exportReady(ready.osExportLocationId(), ready.exportLocationPath()).accessCreated(ready.osGrantAccessId())
          .ready(ready.accessKey()), version, timestamp);
    } else if (event instanceof ShareCreationFailed failed) {
      mapper.upsertShare(userId, new UserShareState(failed.shareId(), failed.spec(), UserShareState.Phase.FAILED,
          failed.osShareId(), failed.osExportLocationId(), failed.exportLocationPath(), failed.osGrantAccessId(),
          null, 0, failed.failure(), null), version, timestamp);
    } else if (event instanceof NetworkCreationRequested requested) {
      upsertNetwork(mapper, userId, UserNetworkState.requested(requested.spec(), requested.osProjectId(), requested.deadline()),
          version, timestamp);
    } else if (event instanceof NetworkBecameReady ready) {
      upsertNetwork(mapper, userId, UserNetworkState.requested(ready.spec(), ready.osProjectId(), null).ready(ready.resources()),
          version, timestamp);
    } else if (event instanceof NetworkCreationFailed failed) {
      upsertNetwork(mapper, userId, UserNetworkState.requested(failed.spec(), failed.osProjectId(), null).failed(failed.failure()),
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
