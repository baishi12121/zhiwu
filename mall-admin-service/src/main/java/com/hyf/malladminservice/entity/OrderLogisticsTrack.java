package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单物流轨迹。表中没有 create_time/update_time，因此不继承 BaseEntity。
 */
@Data
@TableName("order_logistics_track")
public class OrderLogisticsTrack {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderLogisticsId;
    private String content;
    private LocalDateTime occurTime;
    private Integer sortOrder;
}
