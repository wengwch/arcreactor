package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.web.params.CreateRegionParam;
import cn.veryai.arcreactor.web.params.SaveRegionParam;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {
    private final RegionRepo repo;
    private final OpenstackClusterRepo clusterRepo;

    public List<RegionEntity> list(String clusterId) {
        if (clusterId == null) return repo.findAll();
        requireCluster(clusterId);
        return repo.findByClusterId(clusterId);
    }

    public RegionEntity get(String id) {
        RegionEntity entity = repo.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found");
        }
        return entity;
    }

    @Transactional
    public RegionEntity create(CreateRegionParam param) {
        requireCluster(param.getClusterId());
        RegionEntity entity = toEntity(param.getId(), param);
        try {
            repo.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Region ID already exists");
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Region violates database constraints");
        }
        return entity;
    }

    @Transactional
    public RegionEntity update(String id, SaveRegionParam param) {
        get(id);
        requireCluster(param.getClusterId());
        RegionEntity entity = toEntity(id, param);
        try {
            // MySQL may report zero when all values are unchanged.
            if (repo.update(entity) == 0) get(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Region violates database constraints");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Region is still referenced");
        }
    }

    private void requireCluster(String clusterId) {
        if (clusterRepo.findById(clusterId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OpenStack cluster not found");
        }
    }

    private RegionEntity toEntity(String id, SaveRegionParam param) {
        RegionEntity entity = new RegionEntity();
        entity.setId(id);
        entity.setName(param.getName());
        entity.setDesc(param.getDesc());
        entity.setClusterId(param.getClusterId());
        entity.setEnabled(param.getEnabled());
        return entity;
    }
}
