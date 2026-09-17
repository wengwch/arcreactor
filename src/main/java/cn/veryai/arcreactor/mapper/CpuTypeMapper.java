package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.CpuTypeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CpuTypeMapper {
    int insert(CpuTypeEntity entity);

    CpuTypeEntity findById(@Param("id") String id);

    List<CpuTypeEntity> findAll();

    int update(CpuTypeEntity entity);

    int deleteById(@Param("id") String id);
}
