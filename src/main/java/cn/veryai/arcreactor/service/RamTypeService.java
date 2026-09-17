package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.RamTypeEntity;
import cn.veryai.arcreactor.repo.RamTypeRepo;
import cn.veryai.arcreactor.web.params.SaveRamTypeParam;
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
public class RamTypeService {
    private final RamTypeRepo repo;

    public List<RamTypeEntity> list() {
        return repo.findAll();
    }

    public RamTypeEntity get(String id) {
        RamTypeEntity entity = repo.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RAM type not found");
        }
        return entity;
    }

    @Transactional
    public RamTypeEntity create(SaveRamTypeParam param) {
        RamTypeEntity entity = toEntity(UUID.randomUUID().toString(), param);
        try {
            repo.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RAM type name already exists");
        }
        return entity;
    }

    @Transactional
    public RamTypeEntity update(String id, SaveRamTypeParam param) {
        get(id);
        RamTypeEntity entity = toEntity(id, param);
        try {
            int updated = repo.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RAM type name already exists");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RAM type not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RAM type is still referenced");
        }
    }

    private RamTypeEntity toEntity(String id, SaveRamTypeParam param) {
        RamTypeEntity entity = new RamTypeEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
