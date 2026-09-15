package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OpenstackClusterMapper {
    int insert(OpenstackClusterEntity entity);

    OpenstackClusterEntity findById(@Param("id") String id);

    List<OpenstackClusterEntity> findAll();

    int update(OpenstackClusterEntity entity);

    int deleteById(@Param("id") String id);
}
