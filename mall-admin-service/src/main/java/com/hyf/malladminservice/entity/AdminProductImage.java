package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品图片实体，映射 {@code product_image} 表。
 *
 * <p>该表无 create_time/update_time，不继承 {@link com.hyf.mallcommon.mybatis.entity.BaseEntity}。
 *
 * @author hyf
 */
@Data
@TableName("product_image")
public class AdminProductImage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    /** 1 主图 2 详情图 */
    private Integer imageType;
    private String imageUrl;
    private Integer sortOrder;
}
