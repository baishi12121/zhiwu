package com.hyf.mallseckillservice.service;

import com.hyf.mallseckillservice.dto.SeckillOrderMessageDTO;
import com.hyf.mallseckillservice.dto.SeckillOrderResultDTO;

public interface SeckillOrderService {

    public static final int PAY_TIMEOUT_MINUTES = 15;
    public SeckillOrderResultDTO createSeckillOrder(SeckillOrderMessageDTO dto);

}
