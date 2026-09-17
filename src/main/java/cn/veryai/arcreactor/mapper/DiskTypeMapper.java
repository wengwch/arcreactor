package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.DiskTypeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiskTypeMapper {
    int insert(DiskTypeEntity entity);

    DiskTypeEntity findById(@Param("id") String id);

    List<DiskTypeEntity> findAll();

    int update(DiskTypeEntity entity);

    int deleteById(@Param("id") String id);
}
