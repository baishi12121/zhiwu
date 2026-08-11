package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品详情属性（键值对），映射 {@code product_property} 表。
 *
 * @author hyf
 */
@Data
@TableName("product_property")
public class AdminProductProperty implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String name;
    private String value;
    private Integer sortOrder;
}
