package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.ImageCategoryEntity;
import cn.veryai.arcreactor.repo.ImageCategoryRepo;
import cn.veryai.arcreactor.web.params.SaveImageCategoryParam;
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
public class ImageCategoryService {
    private final ImageCategoryRepo repo;

    public List<ImageCategoryEntity> list() {
        return repo.findAll();
    }

    public ImageCategoryEntity get(String id) {
        ImageCategoryEntity entity = repo.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image category not found");
        }
        return entity;
    }

    @Transactional
    public ImageCategoryEntity create(SaveImageCategoryParam param) {
        ImageCategoryEntity entity = toEntity(UUID.randomUUID().toString(), param);
        try {
            repo.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Image category ID already exists");
        }
        return entity;
    }

    @Transactional
    public ImageCategoryEntity update(String id, SaveImageCategoryParam param) {
        get(id);
        ImageCategoryEntity entity = toEntity(id, param);
        try {
            int updated = repo.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Image category ID already exists");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image category not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Image category is still referenced");
        }
    }

    private ImageCategoryEntity toEntity(String id, SaveImageCategoryParam param) {
        ImageCategoryEntity entity = new ImageCategoryEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
