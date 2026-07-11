package com.hyf.mallproductservice.interfaces.rest;

import com.hyf.mallcommon.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 商品域 Controller（骨架，interfaces 层）
 *
 * <p>目标接口（{@code doc/API接口文档.md} §6~§8）：
 * <ul>
 *   <li>GET /home/banners、/quickCategories、/flashSale、/recommend</li>
 *   <li>GET /categories、/categories/{id}</li>
 *   <li>GET /products、/products/{id}、/products/{id}/skus</li>
 *   <li>GET /products/hot            热门榜单</li>
 *   <li>POST /products/click/{id}    点击埋点</li>
 *   <li>POST /internal/products/decrease-stock   内部扣库存</li>
 *   <li>GET /health、/upload、/dict/{type}</li>
 * </ul>
 *
 * @author hyf
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "service", "mall-product-service",
                "status", "UP",
                "layer", "DDD: interfaces/application/domain/infrastructure"
        ));
    }
}
