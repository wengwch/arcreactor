package cn.veryai.arcreactor.actor.user.projection;

import cn.veryai.arcreactor.actor.user.UserActor;
import cn.veryai.arcreactor.actor.user.event.ProjectUpdated;
import cn.veryai.arcreactor.actor.user.event.UserEvent;
import cn.veryai.arcreactor.actor.user.model.UserProjectState;
import cn.veryai.arcreactor.actor.user.projection.mybatis.UserProjectionMapper;
import cn.veryai.arcreactor.entity.UserClusterProjectEntity;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

/** Projects successfully provisioned projects; all other user events are ignored. */
public final class UserProjectionHandler
    extends JdbcHandler<EventEnvelope<UserEvent>, JdbcProjectionSession> {

  @Override
  public void process(JdbcProjectionSession session, EventEnvelope<UserEvent> envelope) {
    if (!(envelope.event() instanceof ProjectUpdated updated)
        || updated.project().phase() != UserProjectState.Phase.READY) {
      return;
    }

    UserProjectState project = updated.project();
    if (project.clusterId() == null || project.clusterId().isBlank()
        || project.projectId() == null || project.projectId().isBlank()) {
      throw new IllegalArgumentException("ready project must have cluster and project IDs");
    }

    UserClusterProjectEntity entity = new UserClusterProjectEntity();
    entity.setUserId(entityId(envelope.persistenceId()));
    entity.setClusterId(project.clusterId());
    entity.setProjectId(project.projectId());
    session.mapper(UserProjectionMapper.class).upsertProject(entity);
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
