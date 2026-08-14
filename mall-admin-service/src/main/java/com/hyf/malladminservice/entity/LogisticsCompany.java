package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 快递公司。表中没有 create_time/update_time，因此不继承 BaseEntity。
 */
@Data
@TableName("logistics_company")
public class LogisticsCompany {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private String tel;
    private Integer sortOrder;
}
