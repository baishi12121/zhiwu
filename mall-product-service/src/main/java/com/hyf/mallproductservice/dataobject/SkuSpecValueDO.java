package com.hyf.mallproductservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * SKU ↔ 规格值关联 DO — 对应 {@code sku_spec_value} 表.
 *
 * @author hyf
 */
@Data
@TableName("sku_spec_value")
public class SkuSpecValueDO {

    private Long id;
    private Long skuId;
    private Long specId;
    private Long specValueId;
    private Integer sortOrder;
}
