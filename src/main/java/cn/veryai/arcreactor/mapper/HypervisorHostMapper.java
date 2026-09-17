package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HypervisorHostMapper {
    int insert(HypervisorHostEntity entity);

    HypervisorHostEntity findById(@Param("id") String id);

    List<HypervisorHostEntity> findAll();

    int update(HypervisorHostEntity entity);

    int deleteById(@Param("id") String id);
}
