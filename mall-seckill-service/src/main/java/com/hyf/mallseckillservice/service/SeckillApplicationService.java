package com.hyf.mallseckillservice.service;

import com.hyf.mallseckillservice.dto.ExecuteReqDTO;
import com.hyf.mallseckillservice.dto.ExecuteResultDTO;
import com.hyf.mallseckillservice.dto.SeckillResultDTO;

public interface SeckillApplicationService {

    public void warmUp();
    public void refreshActiveItemMeta();
    public ExecuteResultDTO execute(Long userId, Long activityId, ExecuteReqDTO req);
    public SeckillResultDTO result(Long userId, Long activityId, Long seckillItemId);
    public String buildMessageId(Long userId, Long activityId, Long seckillItemId);
    public void recoverOrphanInflightDeducts();

}
