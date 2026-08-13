package com.hyf.mallseckillservice.service;

import com.hyf.mallseckillservice.dto.SeckillOrderMessageDTO;
import com.hyf.mallseckillservice.entity.MqMessageDO;

public interface MqMessageService {

    public void createPending(SeckillOrderMessageDTO dto);
    public void markSending(String messageId);
    public void resetFailedToSending(String messageId);
    public void markSent(String messageId);
    public void markFailed(String messageId);
    public void markDone(String messageId);
    public MqMessageDO findByMessageId(String messageId);
    public void sendOrderMessage(SeckillOrderMessageDTO dto);
    public void retryExpired(int batchSize);

}
