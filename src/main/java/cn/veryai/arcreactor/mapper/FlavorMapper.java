package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.FlavorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FlavorMapper {
    int insert(FlavorEntity entity);

    FlavorEntity findById(@Param("id") String id);

    List<FlavorEntity> findAll();

    List<FlavorEntity> findByRegionId(@Param("regionId") String regionId);

    int update(FlavorEntity entity);

    int deleteById(@Param("id") String id);
}
