package com.hyf.mallproductservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 SKU DO — 对应 {@code product_sku} 表.
 *
 * @author hyf
 */
@Data
@TableName("product_sku")
public class ProductSkuDO {

    private Long id;
    private Long productId;
    private String skuCode;
    private BigDecimal price;
    private BigDecimal oldPrice;
    private Integer inventory;
    private String picture;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
