package com.hyf.mallproductservice.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 热门推荐活动 DO — 对应 {@code hot_activity} 表.
 *
 * @author hyf
 */
@Data
@TableName("hot_activity")
public class HotActivityDO {

    private Long id;
    /** preference / inVogue / oneStop / new */
    private String activityKey;
    private String title;
    private String bannerPicture;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
}
