package com.hyf.malladminservice.controller;

import com.hyf.malladminservice.service.AdminSalesService;
import com.hyf.mallcommon.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 销量管理 Controller。
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET /admin/sales/overview     —— 销量总览（总订单 / 总销量 / 总销售额 / 总用户数）</li>
 *   <li>GET /admin/sales/products     —— 商品销量排行 Top N</li>
 *   <li>GET /admin/sales/categories   —— 一级分类销售额分布</li>
 *   <li>GET /admin/sales/trend/daily  —— 按日销量趋势</li>
 * </ul>
 *
 * <p>所有统计仅统计已付款订单（order_state IN 2,3,4,5）。
 *
 * @author hyf
 */
@RestController
@RequestMapping("/admin/sales")
@RequiredArgsConstructor
public class AdminSalesController {

    private final AdminSalesService adminSalesService;

    /** 销量总览 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(adminSalesService.overview());
    }

    /** 商品销量排行 */
    @GetMapping("/products")
    public Result<List<Map<String, Object>>> productRanking(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return Result.success(adminSalesService.productRanking(limit));
    }

    /** 一级分类销售额分布 */
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categoryDistribution() {
        return Result.success(adminSalesService.categoryDistribution());
    }

    /**
     * 按日销量趋势。
     *
     * @param startDate 起始日期（含），ISO 格式 yyyy-MM-dd，默认最近 7 天
     * @param endDate   结束日期（含），默认今天
     */
    @GetMapping("/trend/daily")
    public Result<List<Map<String, Object>>> dailyTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(adminSalesService.dailyTrend(startDate, endDate));
    }
}
