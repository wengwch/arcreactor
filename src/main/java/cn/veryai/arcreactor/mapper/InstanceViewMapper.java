package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.entity.InstanceViewEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstanceViewMapper {
    List<InstanceViewEntity> findByUser(
            @Param("userId") String userId,
            @Param("regionId") String regionId,
            @Param("phase") InstancePhase phase);

    InstanceViewEntity findByUserAndId(
            @Param("userId") String userId,
            @Param("instanceId") String instanceId);
}
