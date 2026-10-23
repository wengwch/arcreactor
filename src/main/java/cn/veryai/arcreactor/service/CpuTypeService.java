package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.entity.CpuTypeEntity;
import cn.veryai.arcreactor.repo.CpuTypeRepo;
import cn.veryai.arcreactor.web.params.SaveCpuTypeParam;
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
public class CpuTypeService {
    private final CpuTypeRepo repo;

    public List<CpuTypeEntity> list() {
        return repo.findAll();
    }

    public CpuTypeEntity get(String id) {
        CpuTypeEntity entity = repo.findById(id);
        if (entity == null) {
            throw new BaseException(ErrorCode.CPU_TYPE_NOT_FOUND);
        }
        return entity;
    }

    @Transactional
    public CpuTypeEntity create(SaveCpuTypeParam param) {
        CpuTypeEntity entity = toEntity(UUID.randomUUID().toString(), param);
        try {
            repo.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BaseException(ErrorCode.CPU_TYPE_ALREADY_EXISTS);
        }
        return entity;
    }

    @Transactional
    public CpuTypeEntity update(String id, SaveCpuTypeParam param) {
        get(id);
        CpuTypeEntity entity = toEntity(id, param);
        try {
            int updated = repo.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new BaseException(ErrorCode.CPU_TYPE_ALREADY_EXISTS);
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
                throw new BaseException(ErrorCode.CPU_TYPE_NOT_FOUND);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BaseException(ErrorCode.CPU_TYPE_STILL_REFERENCED);
        }
    }

    private CpuTypeEntity toEntity(String id, SaveCpuTypeParam param) {
        CpuTypeEntity entity = new CpuTypeEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
