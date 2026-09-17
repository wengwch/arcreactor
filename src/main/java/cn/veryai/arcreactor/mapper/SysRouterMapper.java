package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.SysRouterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRouterMapper {
    int insert(SysRouterEntity entity);

    SysRouterEntity findById(@Param("id") String id);

    List<SysRouterEntity> findAll();

    List<SysRouterEntity> findByRegionId(@Param("regionId") String regionId);

    int update(SysRouterEntity entity);

    int deleteById(@Param("id") String id);
}
