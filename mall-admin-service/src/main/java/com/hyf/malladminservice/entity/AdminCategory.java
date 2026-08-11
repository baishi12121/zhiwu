package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品分类，映射 {@code category} 表（管理后台筛选下拉用）。
 *
 * @author hyf
 */
@Data
@TableName("category")
public class AdminCategory implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private String icon;
    private String picture;
    private Integer sortOrder;
    /** 0 下线 1 正常 */
    private Integer status;
}
