package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.GpuTypeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GpuTypeMapper {
    int insert(GpuTypeEntity entity);

    GpuTypeEntity findById(@Param("id") String id);

    List<GpuTypeEntity> findAll();

    int update(GpuTypeEntity entity);

    int deleteById(@Param("id") String id);
}
