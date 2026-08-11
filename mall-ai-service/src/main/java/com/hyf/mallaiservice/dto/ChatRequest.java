package com.hyf.mallaiservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 智能客服聊天请求
 *
 * 前端传入的用户自然语言问题
 *
 * @author hyf
 */
public class ChatRequest {

    /** 用户提问内容 */
    @NotBlank(message = "问题内容不能为空")
    @Size(max = 500, message = "问题内容不能超过500字")
    private String query;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
