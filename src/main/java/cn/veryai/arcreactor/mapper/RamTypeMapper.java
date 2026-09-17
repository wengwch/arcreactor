package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.RamTypeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RamTypeMapper {
    int insert(RamTypeEntity entity);

    RamTypeEntity findById(@Param("id") String id);

    List<RamTypeEntity> findAll();

    int update(RamTypeEntity entity);

    int deleteById(@Param("id") String id);
}
