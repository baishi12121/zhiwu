package com.hyf.malladminservice.service.impl;

import com.hyf.malladminservice.dto.request.ProductSaveRequest;
import com.hyf.malladminservice.entity.AdminCategory;
import com.hyf.malladminservice.entity.AdminProduct;
import com.hyf.malladminservice.entity.AdminProductImage;
import com.hyf.malladminservice.mapper.AdminCategoryMapper;
import com.hyf.malladminservice.mapper.AdminProductImageMapper;
import com.hyf.malladminservice.mapper.AdminProductMapper;
import com.hyf.malladminservice.mapper.AdminProductPropertyMapper;
import com.hyf.malladminservice.mapper.AdminProductSkuMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminProductServiceImplTest {

    @Test
    void updateProductReplacesSubmittedImages() {
        AdminProductMapper productMapper = mock(AdminProductMapper.class);
        AdminProductSkuMapper skuMapper = mock(AdminProductSkuMapper.class);
        AdminProductImageMapper imageMapper = mock(AdminProductImageMapper.class);
        AdminProductPropertyMapper propertyMapper = mock(AdminProductPropertyMapper.class);
        AdminCategoryMapper categoryMapper = mock(AdminCategoryMapper.class);

        AdminProduct product = new AdminProduct();
        product.setId(7L);
        product.setCategoryId(8L);
        product.setName("智能运动手表");
        product.setPrice(new BigDecimal("599.00"));
        when(productMapper.selectById(7L)).thenReturn(product);
        when(categoryMapper.selectById(8L)).thenReturn(new AdminCategory());

        ProductSaveRequest request = new ProductSaveRequest();
        request.setCategoryId(8L);
        request.setName("智能运动手表");
        request.setPrice(new BigDecimal("599.00"));
        ProductSaveRequest.AdminProductImage image = new ProductSaveRequest.AdminProductImage();
        image.setImageType(1);
        image.setImageUrl("https://example.com/watch.jpg");
        image.setSortOrder(0);
        request.setImages(List.of(image));

        AdminProductServiceImpl service = new AdminProductServiceImpl(
                productMapper,
                skuMapper,
                imageMapper,
                propertyMapper,
                categoryMapper
        );

        service.updateProduct(7L, request);

        verify(imageMapper).delete(any());
        ArgumentCaptor<AdminProductImage> imageCaptor = ArgumentCaptor.forClass(AdminProductImage.class);
        verify(imageMapper).insert(imageCaptor.capture());
        assertThat(imageCaptor.getValue().getProductId()).isEqualTo(7L);
        assertThat(imageCaptor.getValue().getImageUrl()).isEqualTo("https://example.com/watch.jpg");
        assertThat(imageCaptor.getValue().getImageType()).isEqualTo(1);
    }
}
