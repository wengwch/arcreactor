package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.CpuTypeEntity;
import cn.veryai.arcreactor.mapper.CpuTypeMapper;
import cn.veryai.arcreactor.web.params.SaveCpuTypeParam;
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
public class CpuTypeService {
    private final CpuTypeMapper mapper;

    public List<CpuTypeEntity> list() {
        return mapper.findAll();
    }

    public CpuTypeEntity get(String id) {
        CpuTypeEntity entity = mapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CPU type not found");
        }
        return entity;
    }

    @Transactional
    public CpuTypeEntity create(SaveCpuTypeParam param) {
        CpuTypeEntity entity = toEntity(UUID.randomUUID().toString(), param);
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CPU type name already exists");
        }
        return entity;
    }

    @Transactional
    public CpuTypeEntity update(String id, SaveCpuTypeParam param) {
        get(id);
        CpuTypeEntity entity = toEntity(id, param);
        try {
            int updated = mapper.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CPU type name already exists");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (mapper.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CPU type not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CPU type is still referenced");
        }
    }

    private CpuTypeEntity toEntity(String id, SaveCpuTypeParam param) {
        CpuTypeEntity entity = new CpuTypeEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
