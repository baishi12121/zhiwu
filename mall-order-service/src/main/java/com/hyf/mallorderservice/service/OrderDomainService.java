package com.hyf.mallorderservice.service;

import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallorderservice.dataobject.CouponDO;
import com.hyf.mallorderservice.dataobject.UserAddressDO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单领域服务 — 状态机校验、价格计算、订单号生成等跨实体领域逻辑。
 *
 * @author hyf
 */
@Service
public class OrderDomainService {

    /** 订单状态常量 */
    public static final int STATE_UNPAID = 1;
    public static final int STATE_UNSHIPPED = 2;
    public static final int STATE_UNRECEIVED = 3;
    public static final int STATE_UNREVIEWED = 4;
    public static final int STATE_COMPLETED = 5;
    public static final int STATE_CANCELLED = 6;

    /** 付款超时时间（分钟），同时作为延迟消息的延迟时长 */
    public static final int PAY_TIMEOUT_MINUTES = 1;

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 校验状态流转是否合法。
     *
     * @param fromState 当前状态
     * @param toState   目标状态
     * @throws BizException 非法流转时抛出 {@link ResultCode#ORDER_STATUS_ILLEGAL}
     */
    public void validateStateTransition(int fromState, int toState) {
        boolean valid = switch (fromState) {
            case STATE_UNPAID -> toState == STATE_UNSHIPPED || toState == STATE_CANCELLED;
            case STATE_UNSHIPPED -> toState == STATE_UNRECEIVED;
            case STATE_UNRECEIVED -> toState == STATE_UNREVIEWED;
            case STATE_UNREVIEWED -> toState == STATE_COMPLETED;
            default -> false;
        };
        if (!valid) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL);
        }
    }

    /**
     * 计算优惠金额。
     *
     * <p>满减券：满 thresholdAmount 减 discountAmount；折扣券：总金额 × (1 - discountRate)。
     * 不满足门槛或券无效时返回 0。
     *
     * @param coupon      优惠券模板
     * @param totalMoney  订单总金额
     * @return 优惠金额（不小于 0）
     */
    public BigDecimal calculateDiscount(CouponDO coupon, BigDecimal totalMoney) {
        if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 1) {
            return BigDecimal.ZERO;
        }
        // 校验有效期
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidStart() != null && now.isBefore(coupon.getValidStart())) {
            return BigDecimal.ZERO;
        }
        if (coupon.getValidEnd() != null && now.isAfter(coupon.getValidEnd())) {
            return BigDecimal.ZERO;
        }
        // 校验门槛
        if (totalMoney.compareTo(coupon.getThresholdAmount()) < 0) {
            return BigDecimal.ZERO;
        }
        // 计算优惠
        if (coupon.getCouponType() == 1) {
            // 满减
            return coupon.getDiscountAmount();
        } else if (coupon.getCouponType() == 2) {
            // 折扣：优惠 = 总金额 × (1 - 折扣率)
            BigDecimal discountRate = coupon.getDiscountRate();
            if (discountRate == null || discountRate.compareTo(BigDecimal.ZERO) <= 0
                    || discountRate.compareTo(BigDecimal.ONE) >= 0) {
                return BigDecimal.ZERO;
            }
            return totalMoney.multiply(BigDecimal.ONE.subtract(discountRate))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 生成业务订单号：yyyyMMddHHmmss + 4位随机数。
     *
     * @return 18 位订单号
     */
    public String generateOrderNo() {
        String timePart = LocalDateTime.now().format(ORDER_NO_FORMATTER);
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return timePart + random;
    }

    /**
     * 计算付款截止时间（当前时间 + 30 分钟）。
     *
     * @return 付款截止时间
     */
    public LocalDateTime calculatePayLatestTime() {
        return LocalDateTime.now().plusMinutes(PAY_TIMEOUT_MINUTES);
    }

    /**
     * 计算倒计时剩余秒数。
     *
     * @param orderState    订单状态
     * @param payLatestTime 付款截止时间
     * @return 剩余秒数，-1 表示已超时，非待付款状态返回 0
     */
    public long calculateCountdown(Integer orderState, LocalDateTime payLatestTime) {
        if (orderState == null || orderState != STATE_UNPAID) {
            return 0;
        }
        if (payLatestTime == null) {
            return -1;
        }
        long remaining = java.time.Duration.between(LocalDateTime.now(), payLatestTime).getSeconds();
        return remaining > 0 ? remaining : -1;
    }
}
