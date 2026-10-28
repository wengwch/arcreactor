package cn.veryai.arcreactor.actor.user.reply;

import cn.veryai.arcreactor.actor.user.model.UserProjectState;
import cn.veryai.arcreactor.entity.UserClusterProjectEntity;
import java.io.Serializable;

public record ProjectReply(Status status, String userId, String clusterId, String projectId,
    String detail) implements Serializable {
  public enum Status { IN_PROGRESS, READY, FAILED, REJECTED, NOT_FOUND }

  public static ProjectReply from(String userId, UserProjectState project) {
    Status status = switch (project.phase()) {
      case READY -> Status.READY;
      case FAILED -> Status.FAILED;
      default -> Status.IN_PROGRESS;
    };
    return new ProjectReply(status, userId, project.clusterId(), project.projectId(), project.failureMessage());
  }

  public UserClusterProjectEntity mapping() {
    if (status != Status.READY) throw new IllegalStateException("Project is not ready");
    UserClusterProjectEntity entity = new UserClusterProjectEntity();
    entity.setUserId(userId);
    entity.setClusterId(clusterId);
    entity.setProjectId(projectId);
    return entity;
  }
}
