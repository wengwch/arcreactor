package cn.veryai.arcreactor.actor.fip.projection.mybatis;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FloatingIpProjectionMapper {
  int updatePortMask(
      @Param("identity") FloatingIpIdentity identity,
      @Param("portMask") byte[] portMask,
      @Param("version") long version);

  Long findResourceVersion(@Param("identity") FloatingIpIdentity identity);
}
