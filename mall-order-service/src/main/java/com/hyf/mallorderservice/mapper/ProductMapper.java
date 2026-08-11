package com.hyf.mallorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallorderservice.dataobject.ProductDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品主表 Mapper — 订单服务只读，用于查询商品名称快照。
 *
 * @author hyf
 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductDO> {
}
