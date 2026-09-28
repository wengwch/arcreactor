package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.ImageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ImageMapper {
    int insert(ImageEntity entity);

    ImageEntity findById(@Param("id") String id);

    List<ImageEntity> findAll();

    List<ImageEntity> findByFilters(@Param("regionId") String regionId,
                                    @Param("categoryId") String categoryId);

    int update(ImageEntity entity);

    int deleteById(@Param("id") String id);
}
