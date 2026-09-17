package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.SysNetworkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysNetworkMapper {
    int insert(SysNetworkEntity entity);

    SysNetworkEntity findById(@Param("id") String id);

    List<SysNetworkEntity> findAll();

    List<SysNetworkEntity> findByRegionId(@Param("regionId") String regionId);

    int update(SysNetworkEntity entity);

    int deleteById(@Param("id") String id);
}
