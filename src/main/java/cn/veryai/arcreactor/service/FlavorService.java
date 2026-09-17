package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.FlavorEntity;
import cn.veryai.arcreactor.repo.FlavorRepo;
import cn.veryai.arcreactor.repo.HypervisorTypeRepo;
import cn.veryai.arcreactor.repo.CpuTypeRepo;
import cn.veryai.arcreactor.repo.RamTypeRepo;
import cn.veryai.arcreactor.repo.DiskTypeRepo;
import cn.veryai.arcreactor.repo.GpuTypeRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.web.params.SaveFlavorParam;
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
public class FlavorService {
    private final FlavorRepo repo;
    private final HypervisorTypeRepo hypervisorTypeRepo;
    private final CpuTypeRepo cpuTypeRepo;
    private final RamTypeRepo ramTypeRepo;
    private final DiskTypeRepo diskTypeRepo;
    private final GpuTypeRepo gpuTypeRepo;
    private final RegionRepo regionRepo;

    public List<FlavorEntity> list(String regionId) {
        if (regionId == null) return repo.findAll();
        requireRegion(regionId);
        return repo.findByRegionId(regionId);
    }

    public FlavorEntity get(String id) {
        FlavorEntity entity = repo.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flavor not found");
        }
        return entity;
    }

    @Transactional
    public FlavorEntity create(SaveFlavorParam param) {
        FlavorEntity entity = toEntity(UUID.randomUUID().toString(), param);
        validate(entity);
        try {
            repo.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flavor ID already exists");
        }
        return entity;
    }

    @Transactional
    public FlavorEntity update(String id, SaveFlavorParam param) {
        get(id);
        FlavorEntity entity = toEntity(id, param);
        validate(entity);
        try {
            int updated = repo.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flavor ID already exists");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flavor not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flavor is still referenced");
        }
    }

    private void validate(FlavorEntity entity) {
        requireRegion(entity.getRegionId());
        if (entity.getHypervisorType() != null && hypervisorTypeRepo.findById(entity.getHypervisorType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hypervisorType does not exist");
        }
        if (entity.getCpuType() != null && cpuTypeRepo.findById(entity.getCpuType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cpuType does not exist");
        }
        if (entity.getRamType() != null && ramTypeRepo.findById(entity.getRamType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ramType does not exist");
        }
        if (entity.getDiskType() != null && diskTypeRepo.findById(entity.getDiskType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "diskType does not exist");
        }
        if (entity.getGpuType() != null && gpuTypeRepo.findById(entity.getGpuType()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gpuType does not exist");
        }
    }

    private void requireRegion(String regionId) {
        if (regionRepo.findById(regionId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "regionId does not exist");
        }
    }

    private FlavorEntity toEntity(String id, SaveFlavorParam param) {
        FlavorEntity entity = new FlavorEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
