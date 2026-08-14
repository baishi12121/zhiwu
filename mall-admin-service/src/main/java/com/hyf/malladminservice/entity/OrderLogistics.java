package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单物流。表中没有 update_time，因此不继承 BaseEntity。
 */
@Data
@TableName("order_logistics")
public class OrderLogistics {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long companyId;
    private String logisticsNo;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String companyName;
    @TableField(exist = false)
    private String companyCode;
    @TableField(exist = false)
    private String companyTel;
    @TableField(exist = false)
    private List<OrderLogisticsTrack> track;
}
