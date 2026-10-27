package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.FloatingIpEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FloatingIpMapper {
    int insert(FloatingIpEntity entity);
}
