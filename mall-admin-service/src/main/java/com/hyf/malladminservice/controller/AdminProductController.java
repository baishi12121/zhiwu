package com.hyf.malladminservice.controller;

import com.hyf.malladminservice.dto.request.CategorySaveRequest;
import com.hyf.malladminservice.dto.request.ProductSaveRequest;
import com.hyf.malladminservice.dto.request.SkuSaveRequest;
import com.hyf.malladminservice.dto.request.StatusUpdateRequest;
import com.hyf.malladminservice.dto.request.StockAdjustRequest;
import com.hyf.malladminservice.entity.AdminCategory;
import com.hyf.malladminservice.entity.AdminProduct;
import com.hyf.malladminservice.entity.AdminProductSku;
import com.hyf.malladminservice.service.AdminProductService;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 - 商品管理 Controller。
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET    /admin/products            —— 商品分页（categoryId/keyword/status 筛选）</li>
 *   <li>GET    /admin/products/{id}       —— 商品详情（含 SKU/图片/属性）</li>
 *   <li>POST   /admin/products            —— 新建商品（含 SKU/图片/属性）</li>
 *   <li>PUT    /admin/products/{id}       —— 修改商品主表字段</li>
 *   <li>PUT    /admin/products/{id}/status —— 商品上下架</li>
 *   <li>PUT    /admin/products/{id}/stock  —— 调整 SPU 总库存</li>
 *   <li>DELETE /admin/products/{id}       —— 删除商品（级联 SKU/图片/属性）</li>
 *   <li>GET    /admin/products/{id}/skus  —— 商品 SKU 列表</li>
 *   <li>POST   /admin/products/{id}/skus  —— 新增 SKU</li>
 *   <li>PUT    /admin/skus/{skuId}        —— 修改 SKU</li>
 *   <li>PUT    /admin/skus/{skuId}/status —— SKU 上下架</li>
 *   <li>PUT    /admin/skus/{skuId}/stock  —— 调整 SKU 库存</li>
 *   <li>DELETE /admin/skus/{skuId}        —— 删除 SKU</li>
 *   <li>GET    /admin/categories          —— 全量分类列表（下拉用）</li>
 * </ul>
 *
 * @author hyf
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    // ==================== 商品 SPU ====================

    /** 商品分页查询 */
    @GetMapping("/products")
    public Result<PageResult<AdminProduct>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            PageQuery pageQuery) {
        return Result.success(adminProductService.listProducts(pageQuery, categoryId, keyword, status));
    }

    /** 商品详情 */
    @GetMapping("/products/{id}")
    public Result<AdminProduct> detail(@PathVariable Long id) {
        return Result.success(adminProductService.getProductDetail(id));
    }

    /** 新建商品 */
    @PostMapping("/products")
    public Result<Long> create(@Valid @RequestBody ProductSaveRequest req) {
        return Result.success(adminProductService.createProduct(req));
    }

    /** 修改商品 */
    @PutMapping("/products/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSaveRequest req) {
        adminProductService.updateProduct(id, req);
        return Result.success();
    }

    /** 商品上下架 */
    @PutMapping("/products/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req) {
        adminProductService.updateStatus(id, req.getStatus());
        return Result.success();
    }

    /** 调整 SPU 总库存 */
    @PutMapping("/products/{id}/stock")
    public Result<Void> adjustProductStock(@PathVariable Long id, @Valid @RequestBody StockAdjustRequest req) {
        adminProductService.adjustProductStock(id, req);
        return Result.success();
    }

    /** 删除商品（级联 SKU/图片/属性） */
    @DeleteMapping("/products/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminProductService.deleteProduct(id);
        return Result.success();
    }

    // ==================== SKU ====================

    /** 商品 SKU 列表 */
    @GetMapping("/products/{id}/skus")
    public Result<List<AdminProductSku>> listSkus(@PathVariable Long id) {
        return Result.success(adminProductService.listSkus(id));
    }

    /** 新增 SKU */
    @PostMapping("/products/{id}/skus")
    public Result<Long> addSku(@PathVariable Long id, @Valid @RequestBody SkuSaveRequest req) {
        return Result.success(adminProductService.addSku(id, req));
    }

    /** 修改 SKU */
    @PutMapping("/skus/{skuId}")
    public Result<Void> updateSku(@PathVariable Long skuId, @Valid @RequestBody SkuSaveRequest req) {
        adminProductService.updateSku(skuId, req);
        return Result.success();
    }

    /** SKU 上下架 */
    @PutMapping("/skus/{skuId}/status")
    public Result<Void> updateSkuStatus(@PathVariable Long skuId, @Valid @RequestBody StatusUpdateRequest req) {
        adminProductService.updateSkuStatus(skuId, req.getStatus());
        return Result.success();
    }

    /** 调整 SKU 库存 */
    @PutMapping("/skus/{skuId}/stock")
    public Result<Void> adjustSkuStock(@PathVariable Long skuId, @Valid @RequestBody StockAdjustRequest req) {
        adminProductService.adjustSkuStock(skuId, req);
        return Result.success();
    }

    /** 删除 SKU */
    @DeleteMapping("/skus/{skuId}")
    public Result<Void> deleteSku(@PathVariable Long skuId) {
        adminProductService.deleteSku(skuId);
        return Result.success();
    }

    // ==================== 分类 ====================

    /** 全量分类列表（下拉用） */
    @GetMapping("/categories")
    public Result<List<AdminCategory>> listCategories() {
        return Result.success(adminProductService.listAllCategories());
    }

    /** 新增分类 */
    @PostMapping("/categories")
    public Result<Long> createCategory(@RequestBody CategorySaveRequest req) {
        return Result.success(adminProductService.createCategory(req));
    }

    /** 更新分类 */
    @PutMapping("/categories/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody CategorySaveRequest req) {
        adminProductService.updateCategory(id, req);
        return Result.success();
    }

    /** 删除分类 */
    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        adminProductService.deleteCategory(id);
        return Result.success();
    }
}
