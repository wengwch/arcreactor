package cn.veryai.arcreactor.actor.user.projection.mybatis;

import cn.veryai.arcreactor.entity.UserClusterProjectEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProjectionMapper {
  int upsertProject(UserClusterProjectEntity entity);
}
