package com.hyf.malladminservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品保存请求（新建 / 修改共用）。
 *
 * <p>新建时携带的 SKU / 图片 / 属性集合会一并写入；修改时更新主表字段，并整体替换图片 / 属性集合。
 * SKU 的增删改通过独立接口维护。
 *
 * @author hyf
 */
@Data
public class ProductSaveRequest {

    @NotNull(message = "分类 ID 不能为空")
    private Long categoryId;

    private Long brandId;

    private String spuCode;

    @NotBlank(message = "商品名不能为空")
    private String name;

    private String subtitle;

    private String description;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    private BigDecimal oldPrice;

    private BigDecimal discount;

    @Min(value = 0, message = "库存不能小于 0")
    private Integer inventory;

    /** 0 下架 1 上架 */
    private Integer status;

    private Integer isPreSale;

    /** 新建商品时一并写入的 SKU 列表（修改时忽略） */
    private List<AdminProductSku> skus;

    /** 新建 / 修改商品时维护的主图 / 详情图 */
    private List<AdminProductImage> images;

    /** 新建 / 修改商品时维护的详情属性 */
    private List<AdminProductProperty> properties;

    /** 嵌套 SKU 简化结构，便于前端表单提交 */
    @Data
    public static class AdminProductSku {
        private String skuCode;
        @NotNull @DecimalMin("0.01")
        private BigDecimal price;
        private BigDecimal oldPrice;
        @Min(0)
        private Integer inventory;
        private String picture;
        private Integer status;
    }

    @Data
    public static class AdminProductImage {
        /** 1 主图 2 详情图 */
        private Integer imageType;
        @NotBlank
        private String imageUrl;
        private Integer sortOrder;
    }

    @Data
    public static class AdminProductProperty {
        @NotBlank
        private String name;
        @NotBlank
        private String value;
        private Integer sortOrder;
    }
}
