package com.hyf.mallseckillservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallseckillservice.entity.ProductSkuDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 SKU Mapper。
 *
 * <p>用于预热和建单时读取 SKU 原价、图片等快照字段。</p>
 */
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSkuDO> {
}
