package com.hyf.mallproductservice.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.ProductImageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductImageMapper extends BaseMapper<ProductImageDO> {
}
