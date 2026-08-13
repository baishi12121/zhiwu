package com.hyf.mallseckillservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallseckillservice.entity.ProductDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 SPU Mapper。
 *
 * <p>用于消费者建单时补充商品名称快照。</p>
 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductDO> {
}
