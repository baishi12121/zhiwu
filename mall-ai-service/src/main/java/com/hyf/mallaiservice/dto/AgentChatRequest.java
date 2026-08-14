package com.hyf.mallaiservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 前端调用 Spring Boot 的聊天请求。
 */
public class AgentChatRequest {

    @NotBlank(message = "问题内容不能为空")
    @Size(max = 500, message = "问题内容不能超过500字")
    private String question;

    private String conversationId;

    private String userId;

    private String knowledgeBaseId;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }
}
