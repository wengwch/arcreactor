package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.web.params.SaveOpenstackClusterParam;
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
public class OpenstackClusterService {
    private final OpenstackClusterRepo repo;

    public List<OpenstackClusterEntity> list() {
        return repo.findAll();
    }

    public OpenstackClusterEntity get(String id) {
        OpenstackClusterEntity entity = repo.findById(id);
        if (entity == null) {
            throw new BaseException(ErrorCode.OPENSTACK_CLUSTER_NOT_FOUND);
        }
        return entity;
    }

    @Transactional
    public OpenstackClusterEntity create(SaveOpenstackClusterParam param) {
        OpenstackClusterEntity entity = toEntity(UUID.randomUUID().toString(), param);
        try {
            repo.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BaseException(ErrorCode.OPENSTACK_CLUSTER_ALREADY_EXISTS);
        }
        return entity;
    }

    @Transactional
    public OpenstackClusterEntity update(String id, SaveOpenstackClusterParam param) {
        get(id);
        OpenstackClusterEntity entity = toEntity(id, param);
        try {
            int updated = repo.update(entity);
            // MySQL may report zero for an unchanged row; distinguish it from a missing row.
            if (updated == 0) get(id);
        } catch (DuplicateKeyException exception) {
            throw new BaseException(ErrorCode.OPENSTACK_CLUSTER_ALREADY_EXISTS);
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        try {
            if (repo.deleteById(id) == 0) {
                throw new BaseException(ErrorCode.OPENSTACK_CLUSTER_NOT_FOUND);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BaseException(ErrorCode.OPENSTACK_CLUSTER_STILL_REFERENCED);
        }
    }

    private OpenstackClusterEntity toEntity(String id, SaveOpenstackClusterParam param) {
        OpenstackClusterEntity entity = new OpenstackClusterEntity();
        BeanUtils.copyProperties(param, entity);
        entity.setId(id);
        return entity;
    }
}
