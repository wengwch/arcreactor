package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.enums.HypervisorStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HypervisorHostMapper {
    int insert(HypervisorHostEntity entity);

    int upsertFromOpenstack(HypervisorHostEntity entity);

    HypervisorStatus findStatusByOpenstackIdForUpdate(@Param("regionId") String regionId,
                                                     @Param("osHypervisorId") String osHypervisorId);

    HypervisorHostEntity findById(@Param("id") String id);

    HypervisorHostEntity findByIdForUpdate(@Param("id") String id);

    List<HypervisorHostEntity> findAll();

    int update(HypervisorHostEntity entity);

    int deleteById(@Param("id") String id);
}
