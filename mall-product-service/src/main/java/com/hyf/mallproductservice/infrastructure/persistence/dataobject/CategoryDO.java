package com.hyf.mallproductservice.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类 DO — 对应 {@code category} 表.
 *
 * @author hyf
 */
@Data
@TableName("category")
public class CategoryDO {

    private Long id;
    private Long parentId;
    private String name;
    private String icon;
    private String picture;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
