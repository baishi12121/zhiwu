package com.hyf.mallproductservice.api;

/**
 * 商品域对外 Feign 客户端 / DTO 包
 *
 * <p>供 order-service / marketing-service 通过 Feign 调用：
 * <ul>
 *   <li>POST /internal/products/decrease-stock  扣库存</li>
 *   <li>GET  /internal/products/{id}            商品快照（下单时取价）</li>
 * </ul>
 *
 * @author hyf
 */
public final class ProductApiPackage {

    private ProductApiPackage() {
    }
}
