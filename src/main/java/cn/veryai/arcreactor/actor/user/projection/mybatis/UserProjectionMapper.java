package cn.veryai.arcreactor.actor.user.projection.mybatis;

import cn.veryai.arcreactor.entity.UserRegionProjectEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProjectionMapper {
  int upsertProject(UserRegionProjectEntity entity);
}
