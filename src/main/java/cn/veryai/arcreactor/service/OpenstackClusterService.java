package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.mapper.OpenstackClusterMapper;
import cn.veryai.arcreactor.web.params.SaveOpenstackClusterParam;
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
public class OpenstackClusterService {
    private final OpenstackClusterMapper mapper;

    public List<OpenstackClusterEntity> list() {
        return mapper.findAll();
    }

    public OpenstackClusterEntity get(String id) {
        OpenstackClusterEntity entity = mapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OpenStack cluster not found");
        }
        return entity;
    }

    @Transactional
    public OpenstackClusterEntity create(SaveOpenstackClusterParam param) {
        OpenstackClusterEntity entity = toEntity(UUID.randomUUID().toString(), param);
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OpenStack cluster name already exists");
        }
        return entity;
    }

    @Transactional
    public OpenstackClusterEntity update(String id, SaveOpenstackClusterParam param) {
        get(id);
        OpenstackClusterEntity entity = toEntity(id, param);
        try {
            int updated = mapper.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OpenStack cluster name already exists");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (mapper.deleteById(id) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OpenStack cluster not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OpenStack cluster is still referenced");
        }
    }

    private OpenstackClusterEntity toEntity(String id, SaveOpenstackClusterParam param) {
        OpenstackClusterEntity entity = new OpenstackClusterEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
