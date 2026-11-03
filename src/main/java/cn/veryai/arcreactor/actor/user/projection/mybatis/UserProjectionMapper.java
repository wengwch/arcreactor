package cn.veryai.arcreactor.actor.user.projection.mybatis;

import cn.veryai.arcreactor.actor.user.event.NetworkBecameReady;
import cn.veryai.arcreactor.actor.user.event.NetworkCreationFailed;
import cn.veryai.arcreactor.actor.user.model.UserNetworkState;
import cn.veryai.arcreactor.actor.user.model.UserShareState;
import cn.veryai.arcreactor.entity.UserClusterProjectEntity;
import java.sql.Timestamp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserProjectionMapper {
  int upsertProject(UserClusterProjectEntity entity);

  int upsertShare(
      @Param("userId") String userId,
      @Param("share") UserShareState share,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int upsertNetwork(
      @Param("userId") String userId,
      @Param("network") UserNetworkState network,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int upsertRouter(
      @Param("userId") String userId,
      @Param("network") UserNetworkState network,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int markNetworkReady(
      @Param("userId") String userId,
      @Param("event") NetworkBecameReady event,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int markRouterReady(
      @Param("userId") String userId,
      @Param("event") NetworkBecameReady event,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int markNetworkFailed(
      @Param("userId") String userId,
      @Param("event") NetworkCreationFailed event,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int markRouterFailed(
      @Param("userId") String userId,
      @Param("event") NetworkCreationFailed event,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);
}
