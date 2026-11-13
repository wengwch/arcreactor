package cn.veryai.arcreactor.actor.user.projection.mybatis;

import cn.veryai.arcreactor.actor.user.model.UserNetworkState;
import cn.veryai.arcreactor.actor.user.model.UserProjectState;
import cn.veryai.arcreactor.actor.user.model.UserShareState;
import java.sql.Timestamp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserProjectionMapper {
  int upsertProject(
      @Param("userId") String userId,
      @Param("project") UserProjectState project,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  int upsertShare(
      @Param("userId") String userId,
      @Param("share") UserShareState share,
      @Param("name") String name,
      @Param("description") String description,
      @Param("accessTo") String accessTo,
      @Param("version") long version,
      @Param("updatedAt") Timestamp updatedAt);

  String findSystemShareId(
      @Param("userId") String userId,
      @Param("regionId") String regionId);

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
}
