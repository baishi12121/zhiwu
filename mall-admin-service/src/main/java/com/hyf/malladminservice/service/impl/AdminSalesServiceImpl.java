package com.hyf.malladminservice.service.impl;


import com.hyf.malladminservice.service.AdminAuthService;
import com.hyf.malladminservice.service.AdminBannerService;
import com.hyf.malladminservice.service.AdminProductService;
import com.hyf.malladminservice.service.AdminSalesService;
import com.hyf.malladminservice.service.AdminSeckillService;
import com.hyf.malladminservice.service.AdminUserService;
import com.hyf.malladminservice.mapper.SalesMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销量统计业务逻辑。
 *
 * <p>聚合 {@code order} + {@code order_item} 数据，提供：
 * <ul>
 *   <li>总览（订单数 / 销量 / 销售额 / 用户数）；</li>
 *   <li>商品销量排行 Top N；</li>
 *   <li>一级分类销售额分布；</li>
 *   <li>按日销量趋势。</li>
 * </ul>
 *
 * @author hyf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSalesServiceImpl implements AdminSalesService {

    private final SalesMapper salesMapper;

    /**
     * 销量总览。
     */
    public Map<String, Object> overview() {
        Map<String, Object> raw = salesMapper.overview();
        return raw == null ? new LinkedHashMap<>() : raw;
    }

    /**
     * 商品销量排行。
     *
     * @param limit 取前 N 名，null/<=0 时默认 10
     */
    public List<Map<String, Object>> productRanking(Integer limit) {
        int n = (limit == null || limit <= 0) ? 10 : Math.min(limit, 100);
        return salesMapper.productRanking(n);
    }

    /**
     * 一级分类销售额分布。
     */
    public List<Map<String, Object>> categoryDistribution() {
        return salesMapper.categoryDistribution();
    }

    /**
     * 按日销量趋势。
     *
     * @param startDate 起始日期，null 默认最近 7 天
     * @param endDate   结束日期，null 默认今天
     */
    public List<Map<String, Object>> dailyTrend(LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(6);
        if (start.isAfter(end)) {
            // 防御：起始晚于结束时交换
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        return salesMapper.dailyTrend(start, end);
    }
}
