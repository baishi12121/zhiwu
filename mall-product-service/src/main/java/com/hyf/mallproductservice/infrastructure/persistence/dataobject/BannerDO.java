package com.hyf.mallproductservice.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页轮播 DO — 对应 {@code banner} 表.
 *
 * @author hyf
 */
@Data
@TableName("banner")
public class BannerDO {

    private Long id;
    private String title;
    private String imgUrl;
    private String hrefUrl;
    /** 1=页面, 2=H5, 3=小程序 */
    private Integer type;
    /** 1=首页, 2=分类页 */
    private Integer distributionSite;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
