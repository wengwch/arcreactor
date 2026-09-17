package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.HypervisorTypeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HypervisorTypeMapper {
    int insert(HypervisorTypeEntity entity);

    HypervisorTypeEntity findById(@Param("id") String id);

    List<HypervisorTypeEntity> findAll();

    int update(HypervisorTypeEntity entity);

    int deleteById(@Param("id") String id);
}
