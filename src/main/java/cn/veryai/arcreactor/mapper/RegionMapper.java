package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.RegionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RegionMapper {
  int insert(RegionEntity entity);

  RegionEntity findById(@Param("id") String id);

  List<RegionEntity> findAll();

  List<RegionEntity> findByClusterId(@Param("clusterId") String clusterId);

  int update(RegionEntity entity);

  int deleteById(@Param("id") String id);
}
