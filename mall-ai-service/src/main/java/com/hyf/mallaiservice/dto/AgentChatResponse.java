package com.hyf.mallaiservice.dto;

/**
 * Spring Boot 返回给前端的聊天结果。
 */
public class AgentChatResponse {

    private String answer;

    private String conversationId;

    public AgentChatResponse() {
    }

    public AgentChatResponse(String answer, String conversationId) {
        this.answer = answer;
        this.conversationId = conversationId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
