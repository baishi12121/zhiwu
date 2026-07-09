package com.hyf.mallorderservice.mapper;

import com.hyf.mallorderservice.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 订单 Mapper 接口，定义 tb_order 表的数据访问操作
 */
@Mapper
public interface OrderMapper {

    /**
     * 插入一条订单数据
     *
     * @param order 订单实体对象
     * @return 影响的行数，插入成功返回 1
     */
    int insert(Order order);

    /**
     * 根据订单ID删除订单
     *
     * @param id 订单主键ID
     * @return 影响的行数，删除成功返回 1
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新订单信息（动态更新非空字段）
     *
     * @param order 订单实体对象，需包含 id 属性
     * @return 影响的行数，更新成功返回 1
     */
    int update(Order order);

    /**
     * 根据主键ID查询订单详情
     *
     * @param id 订单主键ID
     * @return 订单实体对象，未找到返回 null
     */
    Order selectById(@Param("id") Long id);

    /**
     * 查询所有订单列表
     *
     * @return 订单实体对象集合
     */
    List<Order> selectAll();

    /**
     * 根据用户ID查询其所有订单
     *
     * @param userId 用户主键ID
     * @return 该用户的订单实体对象集合
     */
    List<Order> selectByUserId(@Param("userId") Long userId);
}
