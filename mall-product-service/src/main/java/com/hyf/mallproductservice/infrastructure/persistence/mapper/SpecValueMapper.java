package com.hyf.mallproductservice.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallproductservice.infrastructure.persistence.dataobject.SpecValueDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SpecValueMapper extends BaseMapper<SpecValueDO> {
}
