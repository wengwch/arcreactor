package cn.veryai.arcreactor.actor.user.projection;

import cn.veryai.arcreactor.actor.user.UserActor;
import cn.veryai.arcreactor.actor.user.event.NetworkUpdated;
import cn.veryai.arcreactor.actor.user.event.ProjectUpdated;
import cn.veryai.arcreactor.actor.user.event.ShareUpdated;
import cn.veryai.arcreactor.actor.user.event.UserEvent;
import cn.veryai.arcreactor.actor.user.model.UserProjectState;
import cn.veryai.arcreactor.actor.user.projection.mybatis.UserProjectionMapper;
import cn.veryai.arcreactor.entity.UserClusterProjectEntity;
import java.sql.Timestamp;
import java.time.Instant;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

/** Projects ready cluster projects and every regional share/network workflow snapshot. */
public final class UserProjectionHandler
    extends JdbcHandler<EventEnvelope<UserEvent>, JdbcProjectionSession> {

  @Override
  public void process(JdbcProjectionSession session, EventEnvelope<UserEvent> envelope) {
    UserEvent event = envelope.event();
    UserProjectionMapper mapper = session.mapper(UserProjectionMapper.class);
    String userId = entityId(envelope.persistenceId());
    long version = envelope.sequenceNr();
    Timestamp timestamp = Timestamp.from(Instant.ofEpochMilli(envelope.timestamp()));

    if (event instanceof ProjectUpdated updated
        && updated.project().phase() == UserProjectState.Phase.READY) {
      UserProjectState project = updated.project();
      if (project.clusterId() == null || project.clusterId().isBlank()
          || project.projectId() == null || project.projectId().isBlank()) {
        throw new IllegalArgumentException("ready project must have cluster and project IDs");
      }
      UserClusterProjectEntity entity = new UserClusterProjectEntity();
      entity.setUserId(userId);
      entity.setClusterId(project.clusterId());
      entity.setProjectId(project.projectId());
      mapper.upsertProject(entity);
    } else if (event instanceof ShareUpdated updated) {
      mapper.upsertShare(userId, updated.share(), version, timestamp);
    } else if (event instanceof NetworkUpdated updated) {
      // Router resources share the network workflow; both rows and the offset commit together.
      mapper.upsertNetwork(userId, updated.network(), version, timestamp);
      mapper.upsertRouter(userId, updated.network(), version, timestamp);
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
