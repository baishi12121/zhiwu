package com.hyf.mallproductservice.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 首页热门推荐卡 DO — 对应 {@code home_hot} 表.
 *
 * @author hyf
 */
@Data
@TableName("home_hot")
public class HomeHotDO {

    private Long id;
    private String title;
    private String alt;
    /** 图片集合 JSON（string[]） */
    private String pictures;
    /** 跳转地址（如 /pages/hot/hot?type=preference） */
    private String target;
    /** 推荐类型 → /hot/{key}（如 preference） */
    private String type;
    private Integer sortOrder;
    private Integer status;
}
