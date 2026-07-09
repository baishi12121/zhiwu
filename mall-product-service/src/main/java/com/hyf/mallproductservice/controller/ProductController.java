package com.hyf.mallproductservice.controller;

import com.hyf.mallproductservice.common.Result;
import com.hyf.mallproductservice.entity.Product;
import com.hyf.mallproductservice.entity.ProductScoreMessage;
import com.hyf.mallproductservice.service.ProductService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 商品管理控制层，提供商品 CRUD 及库存扣减的 RESTful API 接口
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    /**
     * 构造器注入 ProductService
     *
     * @param productService 商品服务业务层接口
     */
    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }
    @Autowired
    private RabbitTemplate rabbitTemplate;
    /**
     * 添加/发布新商品
     *
     * @param product 商品数据体
     * @return 添加成功后的商品详情（含自增ID，且赋能初始上架状态及库存）
     */
    @PostMapping
    public Result<Product> saveProduct(@RequestBody Product product) {
        try {
            Product savedProduct = productService.saveProduct(product);
            return Result.success(savedProduct);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("添加商品失败: " + e.getMessage());
        }
    }

    /**
     * 根据商品ID物理删除商品
     *
     * @param id 商品ID
     * @return 操作描述
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteProduct(@PathVariable("id") Long id) {
        try {
            boolean success = productService.deleteProduct(id);
            if (success) {
                return Result.success("删除商品成功");
            } else {
                return Result.error("删除商品失败，该商品可能不存在");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("删除商品操作失败: " + e.getMessage());
        }
    }

    /**
     * 动态更新商品信息
     *
     * @param product 商品需要修改的数据实体（必须包含 id）
     * @return 操作描述
     */
    @PutMapping
    public Result<String> updateProduct(@RequestBody Product product) {
        try {
            boolean success = productService.updateProduct(product);
            if (success) {
                return Result.success("更新商品成功");
            } else {
                return Result.error("更新商品失败，该商品可能不存在");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("更新商品操作失败: " + e.getMessage());
        }
    }

    /**
     * 根据主键ID获取商品详情
     *
     * @param id 商品ID
     * @return 商品详情
     */
    @GetMapping("/{id}")
    public Result<Product> getProductById(@PathVariable("id") Long id) {
        try {
            Product product = productService.getProductById(id);
            if (product != null) {
                return Result.success(product);
            } else {
                return Result.error(404, "未找到该商品详情");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("查询商品失败: " + e.getMessage());
        }
    }

    /**
     * 获取系统所有已发布/上架的商品列表
     *
     * @return 商品数据集合
     */
    @GetMapping
    public Result<List<Product>> getAllProducts() {
        try {
            List<Product> products = productService.getAllProducts();
            return Result.success(products);
        } catch (Exception e) {
            return Result.error("获取商品列表失败: " + e.getMessage());
        }
    }

    /**
     * 扣减商品库存（远程/内部核心调用，具备扣减数量防超卖校验）
     *
     * @param id 商品ID
     * @param count 扣减数量
     * @return 操作描述
     */
    @PostMapping("/decrease-stock")
    public Result<String> decreaseStock(@RequestParam("id") Long id, @RequestParam("count") Integer count) {
        try {
            boolean success = productService.decreaseStock(id, count);
            if (success) {
                return Result.success("扣减库存成功");
            } else {
                return Result.error(400, "扣减库存失败，可能库存不足或商品已下架");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("扣减库存发生系统异常: " + e.getMessage());
        }
    }

    @PostMapping("/click/{id}")
    public ResponseEntity<String> clickProduct(@PathVariable Long id) {
        // 构造异步消息
        ProductScoreMessage message = new ProductScoreMessage();
        message.setProductId(id);
        message.setActionType("CLICK");
        message.setTimestamp(System.currentTimeMillis());

        // 发送到商品热度交换机
        rabbitTemplate.convertAndSend("exchange.product.rank", "routing.product.click", message);

        return ResponseEntity.ok("success");
    }

    /**
     * 获取商品热度排行榜 TOP N
     *
     * @param topN 获取前 N 名，默认 10
     * @return 热点商品 ID 列表
     */
    @GetMapping("/hot-rank")
    public Result<List<Long>> getHotProductRank(
            @RequestParam(value = "topN", required = false, defaultValue = "10") int topN) {
        // 限制最大查询数量，防止资源滥用
        if (topN > 100) {
            topN = 100;
        }
        if (topN <= 0) {
            topN = 10;
        }
        List<Long> rankList = productService.getHotProductRank(topN);
        return Result.success(rankList);
    }

}
