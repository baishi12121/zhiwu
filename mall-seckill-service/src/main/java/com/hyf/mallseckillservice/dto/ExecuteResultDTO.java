package com.hyf.mallseckillservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 秒杀执行结果。
 *
 * <p>execute 接口只表示请求已进入异步建单流程，最终结果通过 result 接口查询。</p>
 */
@Data
@AllArgsConstructor
public class ExecuteResultDTO {

    private String status;
    private String messageId;
}
