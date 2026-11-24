package cn.veryai.arcreactor.repo;

import cn.veryai.arcreactor.entity.LeaseDueEntry;
import cn.veryai.arcreactor.entity.LeaseViewEntity;
import cn.veryai.arcreactor.mapper.RentalViewMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Queries lease projections without contacting actors or deciding business transitions. */
@Repository
@RequiredArgsConstructor
public class RentalViewRepo {
    private final RentalViewMapper mapper;

    public List<LeaseDueEntry> findDueLeases(Instant cutoff, LeaseDueEntry after, int limit) {
        return mapper.findDueLeases(cutoff, after, limit);
    }

    public List<LeaseViewEntity> findLeasesByUser(String userId) {
        return mapper.findLeasesByUser(userId);
    }
}
