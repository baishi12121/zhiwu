package com.hyf.mallaiservice.client.dto;

import com.hyf.mallaiservice.dto.AgentChatResponse;

/**
 * Python /api/chat 统一响应结构。
 */
public class PythonChatResponse {

    private int code;

    private String message;

    private AgentChatResponse data;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AgentChatResponse getData() {
        return data;
    }

    public void setData(AgentChatResponse data) {
        this.data = data;
    }
}
