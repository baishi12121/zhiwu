package com.hyf.mallproductservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 子类 ↔ 商品关联 DO — 对应 {@code hot_subtype_product} 表.
 *
 * @author hyf
 */
@Data
@TableName("hot_subtype_product")
public class HotSubtypeProductDO {

    private Long id;
    private Long subtypeId;
    private Long productId;
    private Integer sortOrder;
}
