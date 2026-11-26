package cn.veryai.arcreactor.actor.lease.projection.mybatis;

import cn.veryai.arcreactor.actor.lease.model.LeasePeriod;
import cn.veryai.arcreactor.actor.lease.model.LeasePurchase;
import cn.veryai.arcreactor.actor.lease.model.LeaseSettlement;
import cn.veryai.arcreactor.actor.lease.model.RentalPlan;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LeaseProjectionMapper {
    Long findVersionForUpdate(
            @Param("leaseId") String leaseId);

    int insertCreated(
            @Param("leaseId") String leaseId,
            @Param("userId") String userId,
            @Param("plan") RentalPlan plan,
            @Param("autoRenew") boolean autoRenew,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateRenewalPlan(
            @Param("leaseId") String leaseId,
            @Param("plan") RentalPlan plan,
            @Param("planRevision") long planRevision,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateAutoRenew(
            @Param("leaseId") String leaseId,
            @Param("enabled") boolean enabled,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updatePurchaseRequested(
            @Param("leaseId") String leaseId,
            @Param("purchase") LeasePurchase purchase,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updatePurchasePriced(
            @Param("leaseId") String leaseId,
            @Param("amount") BigDecimal amount,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updatePurchaseLocked(
            @Param("leaseId") String leaseId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateSettlementRequested(
            @Param("leaseId") String leaseId,
            @Param("settlement") LeaseSettlement settlement,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateSettlementCompleted(
            @Param("leaseId") String leaseId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updatePeriodEnded(
            @Param("leaseId") String leaseId,
            @Param("endsAt") Instant endsAt,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updatePeriodStarted(
            @Param("leaseId") String leaseId,
            @Param("period") LeasePeriod period,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateTermination(
            @Param("leaseId") String leaseId,
            @Param("terminatedAt") Instant terminatedAt,
            @Param("error") String error,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateTerminated(
            @Param("leaseId") String leaseId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateError(
            @Param("leaseId") String leaseId,
            @Param("error") String error,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateNextAction(
            @Param("leaseId") String leaseId,
            @Param("nextActionAt") Instant nextActionAt,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateVersion(
            @Param("leaseId") String leaseId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);
}
