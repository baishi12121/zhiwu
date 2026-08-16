package com.hyf.malladminservice.service;

import com.hyf.malladminservice.dto.request.CategorySaveRequest;
import com.hyf.malladminservice.dto.request.ProductSaveRequest;
import com.hyf.malladminservice.dto.request.SkuSaveRequest;
import com.hyf.malladminservice.dto.request.StockAdjustRequest;
import com.hyf.malladminservice.entity.AdminCategory;
import com.hyf.malladminservice.entity.AdminProduct;
import com.hyf.malladminservice.entity.AdminProductSku;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import java.util.List;

public interface AdminProductService {

    public PageResult<AdminProduct> listProducts(PageQuery query, Long categoryId, String keyword, Integer status);
    public AdminProduct getProductDetail(Long id);
    public Long createProduct(ProductSaveRequest req);
    public void updateProduct(Long id, ProductSaveRequest req);
    public void updateStatus(Long id, Integer status);
    public void deleteProduct(Long id);
    public void adjustProductStock(Long id, StockAdjustRequest req);
    public List<AdminProductSku> listSkus(Long productId);
    public Long addSku(Long productId, SkuSaveRequest req);
    public void updateSku(Long skuId, SkuSaveRequest req);
    public void deleteSku(Long skuId);
    public void updateSkuStatus(Long skuId, Integer status);
    public void adjustSkuStock(Long skuId, StockAdjustRequest req);
    public List<AdminCategory> listAllCategories();
    public Long createCategory(CategorySaveRequest req);
    public void updateCategory(Long id, CategorySaveRequest req);
    public void deleteCategory(Long id);

}
