package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.ImageEntity;
import cn.veryai.arcreactor.mapper.ImageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access boundary for Image; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class ImageRepo {
    private final ImageMapper mapper;

    public int insert(ImageEntity entity) {
        return mapper.insert(entity);
    }

    public ImageEntity findById(String id) {
        return mapper.findById(id);
    }

    public List<ImageEntity> findAll() {
        return mapper.findAll();
    }

    public List<ImageEntity> findByFilters(String regionId, String categoryId) {
        return mapper.findByFilters(regionId, categoryId);
    }

    public int update(ImageEntity entity) {
        return mapper.update(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
