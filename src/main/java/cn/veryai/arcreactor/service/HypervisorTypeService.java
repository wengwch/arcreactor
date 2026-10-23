package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.entity.HypervisorTypeEntity;
import cn.veryai.arcreactor.repo.HypervisorTypeRepo;
import cn.veryai.arcreactor.repo.CpuTypeRepo;
import cn.veryai.arcreactor.repo.RamTypeRepo;
import cn.veryai.arcreactor.repo.DiskTypeRepo;
import cn.veryai.arcreactor.repo.GpuTypeRepo;
import cn.veryai.arcreactor.web.params.SaveHypervisorTypeParam;
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
public class HypervisorTypeService {
    private final HypervisorTypeRepo repo;
    private final CpuTypeRepo cpuTypeRepo;
    private final RamTypeRepo ramTypeRepo;
    private final DiskTypeRepo diskTypeRepo;
    private final GpuTypeRepo gpuTypeRepo;


    public List<HypervisorTypeEntity> list() {
        return repo.findAll();
    }

    public HypervisorTypeEntity get(String id) {
        HypervisorTypeEntity entity = repo.findById(id);
        if (entity == null) {
            throw new BaseException(ErrorCode.HYPERVISOR_TYPE_NOT_FOUND);
        }
        return entity;
    }

    @Transactional
    public HypervisorTypeEntity create(SaveHypervisorTypeParam param) {
        HypervisorTypeEntity entity = toEntity(UUID.randomUUID().toString(), param);
        validate(entity);
        try {
            repo.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BaseException(ErrorCode.HYPERVISOR_TYPE_ALREADY_EXISTS);
        } catch (DataIntegrityViolationException exception) {
            throw new BaseException(ErrorCode.RESOURCE_CONSTRAINT_VIOLATION);
        }
        return entity;
    }

    @Transactional
    public HypervisorTypeEntity update(String id, SaveHypervisorTypeParam param) {
        get(id);
        HypervisorTypeEntity entity = toEntity(id, param);
        validate(entity);
        try {
            int updated = repo.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new BaseException(ErrorCode.HYPERVISOR_TYPE_ALREADY_EXISTS);
        } catch (DataIntegrityViolationException exception) {
            throw new BaseException(ErrorCode.RESOURCE_CONSTRAINT_VIOLATION);
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
                throw new BaseException(ErrorCode.HYPERVISOR_TYPE_NOT_FOUND);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BaseException(ErrorCode.HYPERVISOR_TYPE_STILL_REFERENCED);
        }
    }

    private void validate(HypervisorTypeEntity entity) {
        if (entity.getCpuType() != null
                && cpuTypeRepo.findById(entity.getCpuType()) == null) {
            throw new BaseException(ErrorCode.CPU_TYPE_NOT_FOUND);
        }
        if (entity.getRamType() != null
                && ramTypeRepo.findById(entity.getRamType()) == null) {
            throw new BaseException(ErrorCode.RAM_TYPE_NOT_FOUND);
        }
        if (entity.getDiskType() != null
                && diskTypeRepo.findById(entity.getDiskType()) == null) {
            throw new BaseException(ErrorCode.DISK_TYPE_NOT_FOUND);
        }
        if (entity.getGpuType() != null
                && gpuTypeRepo.findById(entity.getGpuType()) == null) {
            throw new BaseException(ErrorCode.GPU_TYPE_NOT_FOUND);
        }
    }

    private HypervisorTypeEntity toEntity(String id, SaveHypervisorTypeParam param) {
        HypervisorTypeEntity entity = new HypervisorTypeEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
