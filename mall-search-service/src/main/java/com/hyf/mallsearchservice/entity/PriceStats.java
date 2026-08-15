package com.hyf.mallsearchservice.entity;

import lombok.Data;

/**
 * 价格统计(min/max/avg),供前端构建价格区间 UI.
 *
 * @author hyf
 */
@Data
public class PriceStats {

    private Double min;
    private Double max;
    private Double avg;
}
