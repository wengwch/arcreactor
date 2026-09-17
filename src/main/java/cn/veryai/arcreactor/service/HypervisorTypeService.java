package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.HypervisorTypeEntity;
import cn.veryai.arcreactor.mapper.HypervisorTypeMapper;
import cn.veryai.arcreactor.mapper.CpuTypeMapper;
import cn.veryai.arcreactor.mapper.RamTypeMapper;
import cn.veryai.arcreactor.mapper.DiskTypeMapper;
import cn.veryai.arcreactor.mapper.GpuTypeMapper;
import cn.veryai.arcreactor.web.params.SaveHypervisorTypeParam;
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
public class HypervisorTypeService {
    private final HypervisorTypeMapper mapper;
    private final CpuTypeMapper cpuTypeMapper;
    private final RamTypeMapper ramTypeMapper;
    private final DiskTypeMapper diskTypeMapper;
    private final GpuTypeMapper gpuTypeMapper;


    public List<HypervisorTypeEntity> list() {
        return mapper.findAll();
    }

    public HypervisorTypeEntity get(String id) {
        HypervisorTypeEntity entity = mapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hypervisor type not found");
        }
        return entity;
    }

    @Transactional
    public HypervisorTypeEntity create(SaveHypervisorTypeParam param) {
        HypervisorTypeEntity entity = toEntity(UUID.randomUUID().toString(), param);
        validate(entity);
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hypervisor type already exists");
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Referenced resource changed or database constraint violated");
        }
        return entity;
    }

    @Transactional
    public HypervisorTypeEntity update(String id, SaveHypervisorTypeParam param) {
        get(id);
        HypervisorTypeEntity entity = toEntity(id, param);
        validate(entity);
        try {
            int updated = mapper.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hypervisor type already exists");
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Referenced resource changed or database constraint violated");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (mapper.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hypervisor type not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hypervisor type is still referenced");
        }
    }

    private void validate(HypervisorTypeEntity entity) {
        if (entity.getCpuType() != null
                && cpuTypeMapper.findById(entity.getCpuType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cpuType does not exist");
        }
        if (entity.getRamType() != null
                && ramTypeMapper.findById(entity.getRamType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ramType does not exist");
        }
        if (entity.getDiskType() != null
                && diskTypeMapper.findById(entity.getDiskType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "diskType does not exist");
        }
        if (entity.getGpuType() != null
                && gpuTypeMapper.findById(entity.getGpuType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gpuType does not exist");
        }
    }

    private HypervisorTypeEntity toEntity(String id, SaveHypervisorTypeParam param) {
        HypervisorTypeEntity entity = new HypervisorTypeEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
