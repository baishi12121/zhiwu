package com.hyf.mallorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallorderservice.dataobject.PayRecordDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付记录 Mapper。
 *
 * @author hyf
 */
@Mapper
public interface PayRecordMapper extends BaseMapper<PayRecordDO> {
}
