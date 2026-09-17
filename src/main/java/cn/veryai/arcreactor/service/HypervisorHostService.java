package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.mapper.HypervisorHostMapper;
import cn.veryai.arcreactor.mapper.CpuTypeMapper;
import cn.veryai.arcreactor.mapper.RamTypeMapper;
import cn.veryai.arcreactor.mapper.DiskTypeMapper;
import cn.veryai.arcreactor.mapper.GpuTypeMapper;
import cn.veryai.arcreactor.mapper.HypervisorTypeMapper;
import cn.veryai.arcreactor.mapper.RegionMapper;
import cn.veryai.arcreactor.web.params.SaveHypervisorHostParam;
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
public class HypervisorHostService {
    private final HypervisorHostMapper mapper;
    private final CpuTypeMapper cpuTypeMapper;
    private final RamTypeMapper ramTypeMapper;
    private final DiskTypeMapper diskTypeMapper;
    private final GpuTypeMapper gpuTypeMapper;
    private final HypervisorTypeMapper hypervisorTypeMapper;
    private final RegionMapper regionIdMapper;


    public List<HypervisorHostEntity> list() {
        return mapper.findAll();
    }

    public HypervisorHostEntity get(String id) {
        HypervisorHostEntity entity = mapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hypervisor host not found");
        }
        return entity;
    }

    @Transactional
    public HypervisorHostEntity create(SaveHypervisorHostParam param) {
        HypervisorHostEntity entity = toEntity(UUID.randomUUID().toString(), param);
        validate(entity);
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hypervisor host already exists");
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Referenced resource changed or database constraint violated");
        }
        return entity;
    }

    @Transactional
    public HypervisorHostEntity update(String id, SaveHypervisorHostParam param) {
        get(id);
        HypervisorHostEntity entity = toEntity(id, param);
        validate(entity);
        try {
            int updated = mapper.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hypervisor host already exists");
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Referenced resource changed or database constraint violated");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (mapper.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hypervisor host not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hypervisor host is still referenced");
        }
    }

    private void validate(HypervisorHostEntity entity) {
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
        if (entity.getHypervisorType() != null
                && hypervisorTypeMapper.findById(entity.getHypervisorType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hypervisorType does not exist");
        }
        if (entity.getRegionId() != null
                && regionIdMapper.findById(entity.getRegionId()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "regionId does not exist");
        }
        if (entity.getUsedVcpus() > entity.getVcpus() || entity.getUsedRam() > entity.getRam()
                || entity.getUsedDisk() > entity.getDisk() || entity.getUsedGpus() > entity.getGpus()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Used resources must not exceed total capacity");
        }
    }

    private HypervisorHostEntity toEntity(String id, SaveHypervisorHostParam param) {
        HypervisorHostEntity entity = new HypervisorHostEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
