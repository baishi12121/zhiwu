package com.hyf.mallproductservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * SKU VO — 包含规格文本数组，供前端 SKU 弹窗使用.
 *
 * @author hyf
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuVO {

    private String id;
    private BigDecimal price;
    private BigDecimal oldPrice;
    private Integer inventory;
    private String picture;
    private String skuCode;
    /** 规格集合 [{"name":"颜色","valueName":"瓷白色"}] */
    private List<SkuSpecItem> specs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkuSpecItem {
        private String name;
        private String valueName;
    }
}
