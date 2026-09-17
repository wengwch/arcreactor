package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.repo.HypervisorHostRepo;
import cn.veryai.arcreactor.repo.CpuTypeRepo;
import cn.veryai.arcreactor.repo.RamTypeRepo;
import cn.veryai.arcreactor.repo.DiskTypeRepo;
import cn.veryai.arcreactor.repo.GpuTypeRepo;
import cn.veryai.arcreactor.repo.HypervisorTypeRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
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
    private final HypervisorHostRepo repo;
    private final CpuTypeRepo cpuTypeRepo;
    private final RamTypeRepo ramTypeRepo;
    private final DiskTypeRepo diskTypeRepo;
    private final GpuTypeRepo gpuTypeRepo;
    private final HypervisorTypeRepo hypervisorTypeRepo;
    private final RegionRepo regionIdRepo;


    public List<HypervisorHostEntity> list() {
        return repo.findAll();
    }

    public HypervisorHostEntity get(String id) {
        HypervisorHostEntity entity = repo.findById(id);
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
            repo.insert(entity);
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
            int updated = repo.update(entity);
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
            if (repo.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hypervisor host not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hypervisor host is still referenced");
        }
    }

    private void validate(HypervisorHostEntity entity) {
        if (entity.getCpuType() != null
                && cpuTypeRepo.findById(entity.getCpuType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cpuType does not exist");
        }
        if (entity.getRamType() != null
                && ramTypeRepo.findById(entity.getRamType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ramType does not exist");
        }
        if (entity.getDiskType() != null
                && diskTypeRepo.findById(entity.getDiskType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "diskType does not exist");
        }
        if (entity.getGpuType() != null
                && gpuTypeRepo.findById(entity.getGpuType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gpuType does not exist");
        }
        if (entity.getHypervisorType() != null
                && hypervisorTypeRepo.findById(entity.getHypervisorType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hypervisorType does not exist");
        }
        if (entity.getRegionId() != null
                && regionIdRepo.findById(entity.getRegionId()) == null) {
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
