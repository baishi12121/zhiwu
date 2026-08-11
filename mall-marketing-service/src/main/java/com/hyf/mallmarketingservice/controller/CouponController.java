package com.hyf.mallmarketingservice.controller;

import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallmarketingservice.service.CouponService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 优惠券 Controller — 实现优惠券模块 4 个核心接口。
 *
 * <p>用户 ID 从网关下发的 {@code X-User-Id} 请求头获取。
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET    /coupons               平台券列表（分页，标记 grabbed）</li>
 *   <li>POST   /coupons/{id}/receive  领取优惠券</li>
 *   <li>GET    /coupons/my            我的优惠券（分页，可按状态过滤）</li>
 *   <li>GET    /coupons/available     下单可用券（可按金额过滤+计算优惠）</li>
 * </ul>
 *
 * @author hyf
 */
@RestController
@RequestMapping("/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /** 健康检查 */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "service", "mall-marketing-service",
                "status", "UP",
                "scope", "coupon / group-buy / points / check-in / activity"
        ));
    }

    // ========== 5.1 平台券列表 ==========

    /**
     * 平台券列表 — 分页查询可领取的优惠券，标记当前用户是否已领取。
     */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            PageQuery pageQuery) {
        return Result.success(couponService.listCoupons(userId, pageQuery));
    }

    // ========== 5.2 领取优惠券 ==========

    /**
     * 领取优惠券 — 原子扣库存 + 写 user_coupon（防重复）。
     */
    @PostMapping("/{id}/receive")
    public Result<Map<String, Object>> receive(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return Result.success(couponService.receiveCoupon(userId, id));
    }

    // ========== 5.3 我的优惠券 ==========

    /**
     * 我的优惠券 — 分页查询，可按状态过滤。
     *
     * @param status 0未用 1已用 2过期（不传查全部）
     */
    @GetMapping("/my")
    public Result<PageResult<Map<String, Object>>> my(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Integer status,
            PageQuery pageQuery) {
        return Result.success(couponService.myCoupons(userId, status, pageQuery));
    }

    // ========== 5.4 下单可用券 ==========

    /**
     * 下单可用券 — 查询用户未使用且在有效期内的券。
     *
     * @param amount 订单金额（可选；传则只返回满足门槛的券并计算优惠金额）
     */
    @GetMapping("/available")
    public Result<List<Map<String, Object>>> available(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) BigDecimal amount) {
        return Result.success(couponService.availableCoupons(userId, amount));
    }
}
