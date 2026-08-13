package com.hyf.mallmarketingservice.service.impl;


import com.hyf.mallmarketingservice.service.CouponService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallcommon.mybatis.support.PageQueries;
import com.hyf.mallmarketingservice.entity.Coupon;
import com.hyf.mallmarketingservice.entity.UserCoupon;
import com.hyf.mallmarketingservice.mapper.CouponMapper;
import com.hyf.mallmarketingservice.mapper.UserCouponMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 优惠券应用服务 — 平台券列表、领取、我的券、下单可用券。
 *
 * <p>用户 ID 由网关 {@code X-User-Id} 请求头下发。
 *
 * @author hyf
 */
@Service
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    public CouponServiceImpl(CouponMapper couponMapper, UserCouponMapper userCouponMapper) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
    }

    /**
     * 平台券列表 — 分页查询可领取的优惠券（status=1、在有效期内、有库存），
     * 并标记当前用户是否已领取（grabbed）。
     */
    public PageResult<Map<String, Object>> listCoupons(Long userId, PageQuery pageQuery) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1)
                .gt(Coupon::getRemainStock, 0)
                .le(Coupon::getValidStart, now)
                .ge(Coupon::getValidEnd, now)
                .orderByDesc(Coupon::getCreateTime);

        Page<Coupon> page = PageQueries.toPage(pageQuery);
        Page<Coupon> result = couponMapper.selectPage(page, wrapper);

        // 批量查询用户已领取的券 ID，标记 grabbed
        Set<Long> grabbedIds = (userId != null)
                ? new HashSet<>(userCouponMapper.findGrabbedCouponIds(userId))
                : Collections.emptySet();

        List<Map<String, Object>> items = result.getRecords().stream()
                .map(c -> toCouponMap(c, grabbedIds.contains(c.getId())))
                .collect(Collectors.toList());

        return PageResult.of(items, result.getTotal(),
                Math.toIntExact(result.getCurrent()), Math.toIntExact(result.getSize()));
    }

    /**
     * 领取优惠券 — 原子扣库存 + 写 user_coupon（唯一键 uk_user_coupon 防重复）。
     *
     * @param userId   用户 ID
     * @param couponId 优惠券 ID
     * @return { success, message }
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> receiveCoupon(Long userId, Long couponId) {
        // 1. 查优惠券是否存在且正常
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 1) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "优惠券不存在或已下线");
        }

        // 2. 校验有效期
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidStart() != null && now.isBefore(coupon.getValidStart())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "优惠券尚未开始领取");
        }
        if (coupon.getValidEnd() != null && now.isAfter(coupon.getValidEnd())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "优惠券已过期");
        }

        // 3. 原子扣库存（防超卖，WHERE remain_stock > 0 保证不超卖）
        int affected = couponMapper.decreaseStock(couponId);
        if (affected == 0) {
            throw new BizException(ResultCode.COUPON_SOLD_OUT);
        }

        // 4. 写 user_coupon（唯一键 uk_user_coupon(user_id, coupon_id) 防重复领取）
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(0);
        userCoupon.setGrabTime(now);
        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException e) {
            // 并发重复领取，回滚库存
            couponMapper.increaseStock(couponId);
            throw new BizException(ResultCode.COUPON_DUPLICATE_GRAB);
        }

        return Map.of("success", true, "message", "领取成功");
    }

    /**
     * 我的优惠券 — 分页查询用户已领取的券，可选按状态过滤。
     *
     * @param userId 用户 ID
     * @param status 0未用 1已用 2过期（null 查全部）
     */
    public PageResult<Map<String, Object>> myCoupons(Long userId, Integer status, PageQuery pageQuery) {
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(status != null, UserCoupon::getStatus, status)
                .orderByDesc(UserCoupon::getGrabTime);

        Page<UserCoupon> page = PageQueries.toPage(pageQuery);
        Page<UserCoupon> result = userCouponMapper.selectPage(page, wrapper);

        // 批量查优惠券模板，合并到结果（空页时 loadCouponMap 返回空 Map，安全）
        Map<Long, Coupon> couponMap = loadCouponMap(result.getRecords());
        List<Map<String, Object>> items = result.getRecords().stream()
                .map(uc -> toUserCouponMap(uc, couponMap.get(uc.getCouponId())))
                .collect(Collectors.toList());

        return PageResult.of(items, result.getTotal(),
                Math.toIntExact(result.getCurrent()), Math.toIntExact(result.getSize()));
    }

    /**
     * 下单可用券 — 查询用户未使用且在有效期内的券。
     *
     * @param userId 用户 ID
     * @param amount 订单金额（可选；传则只返回满足门槛的券并计算优惠金额）
     */
    public List<Map<String, Object>> availableCoupons(Long userId, BigDecimal amount) {
        // 1. 查用户未使用的券
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, 0)
                .orderByDesc(UserCoupon::getGrabTime);
        List<UserCoupon> userCoupons = userCouponMapper.selectList(wrapper);
        if (userCoupons.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 批量查券模板
        Map<Long, Coupon> couponMap = loadCouponMap(userCoupons);

        // 3. 过滤有效期 + 门槛，计算优惠金额
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserCoupon uc : userCoupons) {
            Coupon coupon = couponMap.get(uc.getCouponId());
            if (coupon == null) {
                continue;
            }
            // 有效期校验
            if (coupon.getValidStart() != null && now.isBefore(coupon.getValidStart())) {
                continue;
            }
            if (coupon.getValidEnd() != null && now.isAfter(coupon.getValidEnd())) {
                continue;
            }
            // 门槛校验（传了金额才过滤）
            BigDecimal discount = calculateDiscount(coupon, amount);
            if (amount != null && discount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Map<String, Object> m = toUserCouponMap(uc, coupon);
            if (amount != null) {
                m.put("discountAmount", discount);
            }
            result.add(m);
        }
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 批量加载优惠券模板，返回 couponId -> Coupon 的映射。
     */
    private Map<Long, Coupon> loadCouponMap(List<UserCoupon> userCoupons) {
        Set<Long> couponIds = userCoupons.stream()
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toSet());
        if (couponIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return couponMapper.selectBatchIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c));
    }

    /**
     * 计算优惠金额：1满减返 discountAmount，2折扣返 amount*(1-rate)；不满足门槛返 0。
     */
    private BigDecimal calculateDiscount(Coupon coupon, BigDecimal amount) {
        if (amount == null || coupon == null) {
            return BigDecimal.ZERO;
        }
        if (coupon.getThresholdAmount() != null
                && amount.compareTo(coupon.getThresholdAmount()) < 0) {
            return BigDecimal.ZERO;
        }
        // 2折扣
        if (coupon.getCouponType() != null
                && coupon.getCouponType() == 2
                && coupon.getDiscountRate() != null) {
            return amount.subtract(amount.multiply(coupon.getDiscountRate()))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        // 1满减
        return coupon.getDiscountAmount() != null ? coupon.getDiscountAmount() : BigDecimal.ZERO;
    }

    /**
     * 平台券列表项 — 含 grabbed 标记。
     */
    private Map<String, Object> toCouponMap(Coupon c, boolean grabbed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("title", c.getTitle());
        m.put("couponType", c.getCouponType());
        m.put("thresholdAmount", c.getThresholdAmount());
        m.put("discountAmount", c.getDiscountAmount());
        m.put("discountRate", c.getDiscountRate());
        m.put("remainStock", c.getRemainStock());
        m.put("validStart", c.getValidStart());
        m.put("validEnd", c.getValidEnd());
        m.put("grabbed", grabbed);
        return m;
    }

    /**
     * 我的券 / 可用券列表项 — 合并 user_coupon + coupon 信息。
     */
    private Map<String, Object> toUserCouponMap(UserCoupon uc, Coupon coupon) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", uc.getId());
        m.put("couponId", uc.getCouponId());
        m.put("status", uc.getStatus());
        m.put("grabTime", uc.getGrabTime());
        if (coupon != null) {
            m.put("title", coupon.getTitle());
            m.put("couponType", coupon.getCouponType());
            m.put("thresholdAmount", coupon.getThresholdAmount());
            m.put("discountAmount", coupon.getDiscountAmount());
            m.put("discountRate", coupon.getDiscountRate());
            m.put("validStart", coupon.getValidStart());
            m.put("validEnd", coupon.getValidEnd());
        }
        return m;
    }
}
