package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.ImageCategoryEntity;
import cn.veryai.arcreactor.mapper.ImageCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for ImageCategory; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class ImageCategoryRepo {
    private final ImageCategoryMapper mapper;

    public int insert(ImageCategoryEntity entity) {
        return mapper.insert(entity);
    }

    public ImageCategoryEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<ImageCategoryEntity> findAll() {
        return mapper.findAll();
    }

    public int update(ImageCategoryEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
