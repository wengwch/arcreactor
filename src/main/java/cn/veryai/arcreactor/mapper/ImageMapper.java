package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.ImageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ImageMapper {
    ImageEntity findById(@Param("id") String id);
}
