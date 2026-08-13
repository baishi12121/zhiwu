package com.hyf.mallseckillservice.service;

import com.hyf.mallseckillservice.dto.StockCompensateDTO;

public interface SeckillCompensateService {

    public void cancelAndRestore(Long orderId);
    public void restoreForCancel(String orderNo);
    public void restoreForCancel(String orderNo, StockCompensateDTO dto);

}
