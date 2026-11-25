package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.lease.model.RentalPlan;
import cn.veryai.arcreactor.entity.HypervisorTypeEntity;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.i18n.ErrorCode;
import cn.veryai.arcreactor.repo.HypervisorTypeRepo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Quotes rental amounts in CNY yuan, rounded to two decimal places. */
@Service
@RequiredArgsConstructor
public class RentalPriceService {
    private final HypervisorTypeRepo hypervisorTypes;

    public BigDecimal quote(String hypervisorTypeId, RentalPlan plan) {
        HypervisorTypeEntity type = hypervisorTypes.findById(hypervisorTypeId);
        if (type == null) throw new BaseException(ErrorCode.HYPERVISOR_TYPE_NOT_FOUND);
        BigDecimal discount = switch (plan.unit()) {
            case HOUR -> type.getDiscountHourlyPayPrice();
            case DAY -> type.getDiscountDailyPayPrice();
            case WEEK -> type.getDiscountWeeklyPayPrice();
            case MONTH -> type.getDiscountMonthlyPayPrice();
        };
        BigDecimal original = switch (plan.unit()) {
            case HOUR -> type.getOriginalHourlyPayPrice();
            case DAY -> type.getOriginalDailyPayPrice();
            case WEEK -> type.getOriginalWeeklyPayPrice();
            case MONTH -> type.getOriginalMonthlyPayPrice();
        };
        BigDecimal price = discount != null ? discount : original;
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("rental price must be positive");
        }
        BigDecimal amount = price.multiply(BigDecimal.valueOf(plan.quantity())).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) throw new IllegalArgumentException("rental amount must be at least one cent");
        if (amount.precision() > 19) throw new IllegalArgumentException("rental amount exceeds DECIMAL(19,2)");
        return amount;
    }
}
