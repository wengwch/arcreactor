package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.SysRouterEntity;
import cn.veryai.arcreactor.repo.SysRouterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.web.params.SaveSysRouterParam;
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
public class SysRouterService {
    private final SysRouterRepo repo;
    private final RegionRepo regionRepo;

    public List<SysRouterEntity> list(String regionId) {
        if (regionId == null) return repo.findAll();
        requireRegion(regionId);
        return repo.findByRegionId(regionId);
    }

    public SysRouterEntity get(String id) {
        SysRouterEntity entity = repo.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System router not found");
        }
        return entity;
    }

    @Transactional
    public SysRouterEntity create(SaveSysRouterParam param) {
        SysRouterEntity entity = toEntity(UUID.randomUUID().toString(), param);
        requireRegion(entity.getRegionId());
        try {
            repo.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System router ID already exists");
        }
        return entity;
    }

    @Transactional
    public SysRouterEntity update(String id, SaveSysRouterParam param) {
        get(id);
        SysRouterEntity entity = toEntity(id, param);
        requireRegion(entity.getRegionId());
        try {
            int updated = repo.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System router ID already exists");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "System router not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System router is still referenced");
        }
    }

    private void requireRegion(String regionId) {
        if (regionRepo.findById(regionId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "regionId does not exist");
        }
    }

    private SysRouterEntity toEntity(String id, SaveSysRouterParam param) {
        SysRouterEntity entity = new SysRouterEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
