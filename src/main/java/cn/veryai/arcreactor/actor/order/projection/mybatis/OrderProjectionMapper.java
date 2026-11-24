package cn.veryai.arcreactor.actor.order.projection.mybatis;

import cn.veryai.arcreactor.actor.order.model.OrderState.Phase;
import java.sql.Timestamp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderProjectionMapper {
    Long findVersionForUpdate(@Param("orderId") String orderId);

    int insertCreated(
            @Param("orderId") String orderId,
            @Param("userId") String userId,
            @Param("leaseId") String leaseId,
            @Param("intentId") String intentId,
            @Param("amount") long amount,
            @Param("phase") Phase phase,
            @Param("deductAmount") Long deductAmount,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateLocked(
            @Param("orderId") String orderId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateDeductionRequested(
            @Param("orderId") String orderId,
            @Param("deductAmount") long deductAmount,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateDeducted(
            @Param("orderId") String orderId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateInsufficientFunds(
            @Param("orderId") String orderId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateError(
            @Param("orderId") String orderId,
            @Param("error") String error,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);
}
