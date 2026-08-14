package com.hyf.mallseckillservice.controller;

import com.hyf.mallcommon.core.constant.MallConstants;
import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallseckillservice.dto.StockCompensateDTO;
import com.hyf.mallseckillservice.service.SeckillConsumerScaleService;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 秒杀内部接口控制器。
 *
 * <p>面向订单服务调用，用于订单取消后通知秒杀服务做库存回补。</p>
 */
@RestController
@RequestMapping(MallConstants.INTERNAL_PREFIX + "/seckill")
@RequiredArgsConstructor
public class InternalSeckillController {

    private final SeckillCompensateService seckillCompensateService;
    private final SeckillConsumerScaleService seckillConsumerScaleService;

    @PostMapping("/orders/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo,
                               @RequestBody(required = false) StockCompensateDTO dto) {
        seckillCompensateService.restoreForCancel(orderNo, dto);
        return Result.success();
    }

    @PostMapping("/consumer/scale")
    public Result<Map<String, Object>> scaleConsumer(@RequestParam("concurrency") int concurrency) {
        return Result.success(seckillConsumerScaleService.scaleTo(concurrency));
    }
}
