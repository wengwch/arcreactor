package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.PortForwardingEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PortForwardingMapper {
    List<PortForwardingEntity> findByInstanceId(@Param("instanceId") String instanceId);
}
