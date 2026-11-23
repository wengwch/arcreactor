package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.InstanceCurrentQosEntity;
import cn.veryai.arcreactor.entity.InstanceQosRecordEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstanceQosMapper {
    List<InstanceQosRecordEntity> findHistoryByInstanceId(@Param("instanceId") String instanceId);

    InstanceCurrentQosEntity findCurrentByInstanceId(@Param("instanceId") String instanceId);
}
