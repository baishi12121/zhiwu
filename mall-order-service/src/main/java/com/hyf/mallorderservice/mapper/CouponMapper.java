package com.hyf.mallorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallorderservice.dataobject.CouponDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券模板 Mapper — 订单服务只读，用于查询优惠券信息、计算优惠金额。
 *
 * @author hyf
 */
@Mapper
public interface CouponMapper extends BaseMapper<CouponDO> {
}
