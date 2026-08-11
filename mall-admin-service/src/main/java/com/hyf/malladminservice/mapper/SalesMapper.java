package com.hyf.malladminservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 销量统计 Mapper。
 *
 * <p>所有 SQL 都基于 {@code order} + {@code order_item} 两表聚合，仅统计已付款订单
 * （{@code order_state IN (2,3,4,5)}，即待发货及之后的状态），不统计待付款 / 已取消。
 *
 * <p>各方法返回的 Map 字段为字符串 key，直接交给 Controller 序列化输出，避免引入额外 DTO。
 *
 * @author hyf
 */
@Mapper
public interface SalesMapper {

    /**
     * 销量总览：总订单数 / 总销量 / 总销售额 / 总用户数。
     *
     * @return 单行聚合结果
     */
    @Select("""
            SELECT
                (SELECT COUNT(*) FROM `order` WHERE order_state IN (2,3,4,5))                       AS total_orders,
                (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_item oi
                   JOIN `order` o ON o.id = oi.order_id
                  WHERE o.order_state IN (2,3,4,5))                                                 AS total_sales_count,
                (SELECT COALESCE(SUM(oi.real_pay), 0) FROM order_item oi
                   JOIN `order` o ON o.id = oi.order_id
                  WHERE o.order_state IN (2,3,4,5))                                                 AS total_sales_amount,
                (SELECT COUNT(DISTINCT user_id) FROM `order` WHERE order_state IN (2,3,4,5))        AS total_users
            """)
    Map<String, Object> overview();

    /**
     * 商品销量排行 Top N。
     *
     * @param limit 取前 N 名，默认 10
     * @return 排行列表，每行含 spuId / name / salesCount / salesAmount
     */
    @Select("""
            SELECT oi.spu_id           AS spu_id,
                   oi.name             AS spu_name,
                   SUM(oi.quantity)    AS sales_count,
                   SUM(oi.real_pay)    AS sales_amount
            FROM order_item oi
            JOIN `order` o ON o.id = oi.order_id
            WHERE o.order_state IN (2,3,4,5)
            GROUP BY oi.spu_id, oi.name
            ORDER BY sales_count DESC, sales_amount DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> productRanking(@Param("limit") int limit);

    /**
     * 分类销量分布：每个一级分类的销售额占比。
     *
     * <p>通过 product.category_id 上溯到一级分类（parent_id=0）后聚合。
     *
     * @return 每个一级分类的销售额 + 销量
     */
    @Select("""
            SELECT c1.id              AS category_id,
                   c1.name            AS category_name,
                   SUM(oi.quantity)   AS sales_count,
                   SUM(oi.real_pay)   AS sales_amount
            FROM order_item oi
            JOIN `order`      o  ON o.id = oi.order_id
            JOIN product       p  ON p.id = oi.spu_id
            JOIN category      c2 ON c2.id = p.category_id
            JOIN category      c1 ON c1.id = CASE WHEN c2.parent_id = 0 THEN c2.id ELSE c2.parent_id END
            WHERE o.order_state IN (2,3,4,5)
            GROUP BY c1.id, c1.name
            ORDER BY sales_amount DESC
            """)
    List<Map<String, Object>> categoryDistribution();

    /**
     * 按日聚合销量趋势（最近 N 天）。
     *
     * @param startDate 起始日期（含）
     * @param endDate   结束日期（含）
     * @return 每天 1 行：date / orderCount / salesCount / salesAmount
     */
    @Select("""
            SELECT DATE(o.create_time)             AS stat_date,
                   COUNT(DISTINCT o.id)            AS order_count,
                   COALESCE(SUM(oi.quantity), 0)   AS sales_count,
                   COALESCE(SUM(oi.real_pay), 0)   AS sales_amount
            FROM `order` o
            LEFT JOIN order_item oi ON oi.order_id = o.id
            WHERE o.order_state IN (2,3,4,5)
              AND DATE(o.create_time) BETWEEN #{startDate} AND #{endDate}
            GROUP BY DATE(o.create_time)
            ORDER BY stat_date ASC
            """)
    List<Map<String, Object>> dailyTrend(@Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);
}
