package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("banner")
public class AdminBanner implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String imgUrl;
    private String hrefUrl;
    private Integer type;
    private Integer distributionSite;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
