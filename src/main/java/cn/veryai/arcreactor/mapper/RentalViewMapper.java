package cn.veryai.arcreactor.mapper;

import cn.veryai.arcreactor.entity.LeaseDueEntry;
import cn.veryai.arcreactor.entity.LeaseViewEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RentalViewMapper {
    List<LeaseDueEntry> findDueLeases(
            @Param("cutoff") Instant cutoff,
            @Param("after") LeaseDueEntry after,
            @Param("limit") int limit);

    List<LeaseViewEntity> findLeasesByUser(@Param("userId") String userId);
}
