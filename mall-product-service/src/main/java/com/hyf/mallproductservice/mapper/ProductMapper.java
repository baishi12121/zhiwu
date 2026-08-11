package com.hyf.mallproductservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallproductservice.dataobject.ProductDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<ProductDO> {
}
