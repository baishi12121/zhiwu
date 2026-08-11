package com.hyf.malladminservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SKU 库存调整请求。
 *
 * <p>支持两种调整模式：
 * <ul>
 *   <li>{@code absolute=true}（默认）—— 把库存直接设为 {@code inventory}；</li>
 *   <li>{@code absolute=false} —— 在原库存基础上增减 {@code inventory}（可为负数）。</li>
 * </ul>
 *
 * @author hyf
 */
@Data
public class StockAdjustRequest {

    @NotNull(message = "inventory 不能为空")
    private Integer inventory;

    /** 是否绝对值设置，默认 true */
    private Boolean absolute = true;

    @Min(0)
    private Integer limit;
}
