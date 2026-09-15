package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.FlavorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FlavorMapper {
    FlavorEntity findById(@Param("id") String id);
}
