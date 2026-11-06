package cn.veryai.arcreactor.actor.fip.projection.mybatis;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FloatingIpProjectionMapper {
  int updateState(
      @Param("identity") FloatingIpIdentity identity,
      @Param("enabled") boolean enabled,
      @Param("portMask") byte[] portMask,
      @Param("version") long version);

  Long findResourceVersion(@Param("identity") FloatingIpIdentity identity);
}
