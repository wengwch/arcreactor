package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.entity.ImageEntity;
import cn.veryai.arcreactor.repo.ImageRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.repo.ImageCategoryRepo;
import cn.veryai.arcreactor.web.params.SaveImageParam;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            throw new BaseException(ErrorCode.IMAGE_NOT_FOUND);
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
            throw new BaseException(ErrorCode.IMAGE_ALREADY_EXISTS);
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
            throw new BaseException(ErrorCode.IMAGE_ALREADY_EXISTS);
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
                throw new BaseException(ErrorCode.IMAGE_NOT_FOUND);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BaseException(ErrorCode.IMAGE_STILL_REFERENCED);
        }
    }

    private void validate(ImageEntity entity) {
        requireRegion(entity.getRegionId());
        if (entity.getCategoryId() != null) requireCategory(entity.getCategoryId());
    }

    private void requireRegion(String regionId) {
        if (regionRepo.findById(regionId) == null) {
            throw new BaseException(ErrorCode.REGION_NOT_FOUND);
        }
    }

    private void requireCategory(String categoryId) {
        if (categoryRepo.findById(categoryId) == null) {
            throw new BaseException(ErrorCode.IMAGE_CATEGORY_NOT_FOUND);
        }
    }

    private ImageEntity toEntity(String id, SaveImageParam param) {
        ImageEntity entity = new ImageEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
