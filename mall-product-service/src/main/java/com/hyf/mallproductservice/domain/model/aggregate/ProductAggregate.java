package com.hyf.mallproductservice.domain.model.aggregate;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品聚合根（占位）
 *
 * <p>DDD 商品域聚合：
 * <ul>
 *   <li>当前沿用简化版 {@code product} + {@code product_sku} 两表模型</li>
 *   <li>聚合内含：商品基本信息 / 价格 / 库存 / 规格（SKU）/ 详情图</li>
 *   <li>后续如拆 SPU/品牌/规格组，在本聚合内扩展，不影响 interfaces 层</li>
 * </ul>
 *
 * <p>骨架阶段，具体字段待业务实现填充。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ProductAggregate {
    private Long id;
    private String name;
}
