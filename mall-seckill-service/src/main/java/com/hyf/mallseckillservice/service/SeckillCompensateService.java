package com.hyf.mallseckillservice.service;

import com.hyf.mallseckillservice.dto.StockCompensateDTO;

public interface SeckillCompensateService {

    void cancelAndRestore(Long orderId);
    void restoreForCancel(String orderNo);
    void restoreForCancel(String orderNo, StockCompensateDTO dto);
    void restoreForCreateFailure(String messageId, Long activityId, Long seckillItemId, Long userId, int quantity);
    void recordReconcileDiff(String messageId, Long activityId, Long seckillItemId, Long userId, int quantity);

}
