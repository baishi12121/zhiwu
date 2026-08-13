package com.hyf.mallseckillservice.controller;

import com.hyf.mallcommon.core.exception.UnauthorizedException;
import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallcommon.security.context.SecurityContextHolder;
import com.hyf.mallseckillservice.dto.ExecuteReqDTO;
import com.hyf.mallseckillservice.dto.ExecuteResultDTO;
import com.hyf.mallseckillservice.dto.SeckillResultDTO;
import com.hyf.mallseckillservice.service.SeckillApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀用户入口控制器。
 *
 * <p>提供秒杀执行和结果查询接口，用户身份只从安全上下文读取，避免信任可伪造的请求头。</p>
 */
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillApplicationService seckillApplicationService;

    @PostMapping("/{activityId}/execute")
    public Result<ExecuteResultDTO> execute(@PathVariable Long activityId,
                                            @Valid @RequestBody ExecuteReqDTO req) {
        return Result.success(seckillApplicationService.execute(currentUserId(), activityId, req));
    }

    @GetMapping("/{activityId}/result")
    public Result<SeckillResultDTO> result(@PathVariable Long activityId,
                                           @RequestParam Long seckillItemId) {
        return Result.success(seckillApplicationService.result(currentUserId(), activityId, seckillItemId));
    }

    private Long currentUserId() {
        // TokenAuthInterceptor 已把 JWT 解析结果写入 SecurityContextHolder，这里不再读取 X-User-Id。
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return userId;
    }
}
