package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.ImageCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ImageCategoryMapper {
    int insert(ImageCategoryEntity entity);

    ImageCategoryEntity findById(@Param("id") String id);

    List<ImageCategoryEntity> findAll();

    int update(ImageCategoryEntity entity);

    int deleteById(@Param("id") String id);
}
