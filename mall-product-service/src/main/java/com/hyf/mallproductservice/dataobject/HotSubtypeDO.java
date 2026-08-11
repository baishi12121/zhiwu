package com.hyf.mallproductservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 热门推荐子类 Tab DO — 对应 {@code hot_subtype} 表.
 *
 * @author hyf
 */
@Data
@TableName("hot_subtype")
public class HotSubtypeDO {

    private Long id;
    private Long activityId;
    private String title;
    private Integer sortOrder;
}
