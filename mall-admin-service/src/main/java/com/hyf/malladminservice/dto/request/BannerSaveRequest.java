package com.hyf.malladminservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BannerSaveRequest {

    @NotBlank(message = "banner标题不能为空")
    private String title;

    @NotBlank(message = "banner图片不能为空")
    private String imgUrl;

    private String hrefUrl;

    @NotNull(message = "跳转类型不能为空")
    private Integer type;

    @NotNull(message = "投放位置不能为空")
    private Integer distributionSite;

    private Integer sortOrder;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
