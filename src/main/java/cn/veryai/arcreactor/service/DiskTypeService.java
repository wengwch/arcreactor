package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.DiskTypeEntity;
import cn.veryai.arcreactor.repo.DiskTypeRepo;
import cn.veryai.arcreactor.web.params.SaveDiskTypeParam;
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
public class DiskTypeService {
    private final DiskTypeRepo repo;

    public List<DiskTypeEntity> list() {
        return repo.findAll();
    }

    public DiskTypeEntity get(String id) {
        DiskTypeEntity entity = repo.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Disk type not found");
        }
        return entity;
    }

    @Transactional
    public DiskTypeEntity create(SaveDiskTypeParam param) {
        DiskTypeEntity entity = toEntity(UUID.randomUUID().toString(), param);
        try {
            repo.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Disk type name already exists");
        }
        return entity;
    }

    @Transactional
    public DiskTypeEntity update(String id, SaveDiskTypeParam param) {
        get(id);
        DiskTypeEntity entity = toEntity(id, param);
        try {
            int updated = repo.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Disk type name already exists");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Disk type not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Disk type is still referenced");
        }
    }

    private DiskTypeEntity toEntity(String id, SaveDiskTypeParam param) {
        DiskTypeEntity entity = new DiskTypeEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
