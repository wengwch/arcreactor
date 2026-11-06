package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.FloatingIpEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FloatingIpMapper {
    int insert(FloatingIpEntity entity);

    FloatingIpEntity findById(@Param("id") String id);

    List<FloatingIpEntity> findByRegionId(@Param("regionId") String regionId);
}
