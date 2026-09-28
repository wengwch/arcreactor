package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.ImageEntity;
import cn.veryai.arcreactor.repo.ImageRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.repo.ImageCategoryRepo;
import cn.veryai.arcreactor.web.params.SaveImageParam;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImageService {
    private final ImageRepo repo;
    private final RegionRepo regionRepo;
    private final ImageCategoryRepo categoryRepo;

    public List<ImageEntity> list(String regionId, String categoryId) {
        if (regionId != null) requireRegion(regionId);
        if (categoryId != null) requireCategory(categoryId);
        return repo.findByFilters(regionId, categoryId);
    }

    public ImageEntity get(String id) {
        ImageEntity entity = repo.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        return entity;
    }

    @Transactional
    public ImageEntity create(SaveImageParam param) {
        ImageEntity entity = toEntity(UUID.randomUUID().toString(), param);
        validate(entity);
        try {
            repo.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Image ID already exists");
        }
        return entity;
    }

    @Transactional
    public ImageEntity update(String id, SaveImageParam param) {
        get(id);
        ImageEntity entity = toEntity(id, param);
        validate(entity);
        try {
            int updated = repo.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Image ID already exists");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Image is still referenced");
        }
    }

    private void validate(ImageEntity entity) {
        requireRegion(entity.getRegionId());
        if (entity.getCategoryId() != null) requireCategory(entity.getCategoryId());
    }

    private void requireRegion(String regionId) {
        if (regionRepo.findById(regionId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "regionId does not exist");
        }
    }

    private void requireCategory(String categoryId) {
        if (categoryRepo.findById(categoryId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId does not exist");
        }
    }

    private ImageEntity toEntity(String id, SaveImageParam param) {
        ImageEntity entity = new ImageEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
