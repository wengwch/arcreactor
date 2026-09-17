package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.GpuTypeEntity;
import cn.veryai.arcreactor.mapper.GpuTypeMapper;
import cn.veryai.arcreactor.web.params.SaveGpuTypeParam;
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
public class GpuTypeService {
    private final GpuTypeMapper mapper;

    public List<GpuTypeEntity> list() {
        return mapper.findAll();
    }

    public GpuTypeEntity get(String id) {
        GpuTypeEntity entity = mapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GPU type not found");
        }
        return entity;
    }

    @Transactional
    public GpuTypeEntity create(SaveGpuTypeParam param) {
        GpuTypeEntity entity = toEntity(UUID.randomUUID().toString(), param);
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "GPU type name already exists");
        }
        return entity;
    }

    @Transactional
    public GpuTypeEntity update(String id, SaveGpuTypeParam param) {
        get(id);
        GpuTypeEntity entity = toEntity(id, param);
        try {
            int updated = mapper.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "GPU type name already exists");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (mapper.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GPU type not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "GPU type is still referenced");
        }
    }

    private GpuTypeEntity toEntity(String id, SaveGpuTypeParam param) {
        GpuTypeEntity entity = new GpuTypeEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
