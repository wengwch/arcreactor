package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.ImageEntity;
import cn.veryai.arcreactor.mapper.ImageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Data access boundary for Image; cache reads and invalidate writes here when caching is introduced. */
@Repository
@RequiredArgsConstructor
public class ImageRepo {
    private final ImageMapper mapper;

    public ImageEntity findById(String id) {
        return mapper.findById(id);
    }
}
