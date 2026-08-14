package com.hyf.malladminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hyf.malladminservice.entity.AdminOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 管理后台订单 Mapper。
 */
@Mapper
public interface AdminOrderMapper extends BaseMapper<AdminOrder> {

    @Select("""
            <script>
            SELECT o.id, o.order_no, o.user_id, u.nickname, o.order_state, o.order_source,
                   o.receiver_contact, o.receiver_mobile, o.pay_money, o.create_time, o.shipped_at,
                   (SELECT oi.image FROM order_item oi WHERE oi.order_id = o.id ORDER BY oi.id LIMIT 1) AS item_image,
                   (SELECT oi.name FROM order_item oi WHERE oi.order_id = o.id ORDER BY oi.id LIMIT 1) AS item_name,
                   (SELECT COUNT(*) FROM order_item oi WHERE oi.order_id = o.id) AS item_count,
                   (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_item oi WHERE oi.order_id = o.id) AS total_num
            FROM `order` o
            LEFT JOIN `user` u ON u.id = o.user_id
            WHERE 1 = 1
              <if test="orderState != null">AND o.order_state = #{orderState}</if>
              <if test="orderSource != null">AND o.order_source = #{orderSource}</if>
              <if test="keyword != null and keyword != ''">
                AND (o.order_no LIKE CONCAT('%', #{keyword}, '%')
                  OR o.receiver_contact LIKE CONCAT('%', #{keyword}, '%')
                  OR o.receiver_mobile LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="start != null">AND o.create_time &gt;= #{start}</if>
              <if test="end != null">AND o.create_time &lt; DATE_ADD(#{end}, INTERVAL 1 DAY)</if>
            ORDER BY o.create_time DESC
            </script>
            """)
    IPage<AdminOrder> selectAdminPage(IPage<AdminOrder> page,
                                      @Param("orderState") Integer orderState,
                                      @Param("orderSource") Integer orderSource,
                                      @Param("keyword") String keyword,
                                      @Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    @Select("""
            SELECT o.*, u.nickname
            FROM `order` o
            LEFT JOIN `user` u ON u.id = o.user_id
            WHERE o.id = #{id}
            """)
    AdminOrder selectDetailById(@Param("id") Long id);

    @Update("""
            UPDATE `order`
            SET order_state = 3, shipped_at = NOW()
            WHERE id = #{orderId} AND order_state = 2
            """)
    int shipOrder(@Param("orderId") Long orderId);
}
