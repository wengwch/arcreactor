package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.entity.InstanceViewEntity;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.repo.InstanceViewRepo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User-scoped queries of the asynchronously maintained instance read model. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstanceQueryService {
    private final InstanceViewRepo repo;

    public List<InstanceViewEntity> list(String userId, String regionId, InstancePhase phase) {
        requireId(userId);
        String region = StringUtils.trimToNull(regionId);
        if (region != null) requireId(region);
        return repo.findByUser(userId, region, phase);
    }

    public InstanceViewEntity get(String userId, String instanceId) {
        requireId(userId);
        requireId(instanceId);
        InstanceViewEntity instance = repo.findByUserAndId(userId, instanceId);
        if (instance == null) throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        return instance;
    }

    private static void requireId(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new BaseException(ErrorCode.BAD_REQUEST);
        }
    }
}
