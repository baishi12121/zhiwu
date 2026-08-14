package com.hyf.mallseckillservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallseckillservice.entity.SeckillStockCompensateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SeckillStockCompensateMapper extends BaseMapper<SeckillStockCompensateDO> {

    int insertIgnore(SeckillStockCompensateDO compensate);

    int markDone(@Param("messageId") String messageId, @Param("compensateType") int compensateType);

    int markFailed(@Param("messageId") String messageId, @Param("compensateType") int compensateType);

    int countCancellationDone(@Param("messageId") String messageId);
}
