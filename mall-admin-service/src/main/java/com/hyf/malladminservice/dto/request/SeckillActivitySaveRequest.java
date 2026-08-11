package com.hyf.malladminservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀活动保存请求（新建 / 修改共用）。
 *
 * @author hyf
 */
@Data
public class SeckillActivitySaveRequest {

    @NotBlank(message = "活动名不能为空")
    private String name;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    /** 0 禁用 1 启用 */
    private Integer enabled;

    private String remark;
}
