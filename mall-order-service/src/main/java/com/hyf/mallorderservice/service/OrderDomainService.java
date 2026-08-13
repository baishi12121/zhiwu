package com.hyf.mallorderservice.service;

import com.hyf.mallorderservice.dataobject.CouponDO;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface OrderDomainService {

    public static final int STATE_UNPAID = 1;
    public static final int STATE_UNSHIPPED = 2;
    public static final int STATE_UNRECEIVED = 3;
    public static final int STATE_UNREVIEWED = 4;
    public static final int STATE_COMPLETED = 5;
    public static final int STATE_CANCELLED = 6;
    public static final int PAY_TIMEOUT_MINUTES = 1;
    public void validateStateTransition(int fromState, int toState);
    public BigDecimal calculateDiscount(CouponDO coupon, BigDecimal totalMoney);
    public String generateOrderNo();
    public LocalDateTime calculatePayLatestTime();
    public long calculateCountdown(Integer orderState, LocalDateTime payLatestTime);

}
