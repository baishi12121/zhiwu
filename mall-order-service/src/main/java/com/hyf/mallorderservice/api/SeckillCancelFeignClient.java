package com.hyf.mallorderservice.api;

import com.hyf.mallcommon.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "mall-seckill-service", path = "/internal/seckill")
public interface SeckillCancelFeignClient {

    @PostMapping("/orders/{orderNo}/cancel")
    Result<Void> cancelSeckillOrder(@PathVariable("orderNo") String orderNo,
                                    @RequestBody SeckillCancelDTO dto);
}
