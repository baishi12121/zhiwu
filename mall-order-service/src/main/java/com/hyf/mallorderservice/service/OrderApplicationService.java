package com.hyf.mallorderservice.service;

import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallorderservice.dto.OrderCreateRequest;
import com.hyf.mallorderservice.dto.OrderPreviewRequest;
import java.util.Map;

public interface OrderApplicationService {

    public Map<String, Object> previewOrder(Long userId, OrderPreviewRequest request);
    public Map<String, Object> createOrder(Long userId, OrderCreateRequest request);
    public PageResult<Map<String, Object>> getOrderList(Long userId, PageQuery pageQuery, Integer orderState);
    public Map<String, Object> getOrderDetail(Long userId, Long id);
    public Map<String, Object> cancelOrder(Long userId, Long id, String cancelReason);
    public void cancelOrderBySystem(Long orderId);
    public Map<String, Object> payOrder(Long userId, Long id);
    public Map<String, Object> confirmOrder(Long userId, Long id);
    public void deleteOrder(Long userId, Long id);
    public PageResult<Map<String, Object>> getOrdersByStatus(Long userId, Integer status, PageQuery pageQuery);

}
