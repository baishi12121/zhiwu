package com.hyf.mallmarketingservice.service;

import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface CouponService {

    public PageResult<Map<String, Object>> listCoupons(Long userId, PageQuery pageQuery);
    public Map<String, Object> receiveCoupon(Long userId, Long couponId);
    public PageResult<Map<String, Object>> myCoupons(Long userId, Integer status, PageQuery pageQuery);
    public List<Map<String, Object>> availableCoupons(Long userId, BigDecimal amount);

}
