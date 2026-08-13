package com.hyf.mallorderservice.service;

import com.hyf.mallorderservice.service.PayResponse;
import java.util.Map;

public interface PayApplicationService {

    public PayResponse createWxPayOrder(Long userId, Long orderId, String openid);
    public void handleNotify(String body, Map<String, String> headers);
    public Map<String, Object> getPayStatus(Long userId, Long orderId);
    public Map<String, Object> refund(Long userId, Long orderId, String reason);
    public Map<String, Object> getRefundStatus(Long userId, Long orderId);

}
