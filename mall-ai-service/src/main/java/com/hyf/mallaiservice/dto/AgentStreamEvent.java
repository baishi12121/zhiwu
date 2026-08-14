package com.hyf.mallaiservice.dto;

/**
 * AI 流式响应事件。
 */
public class AgentStreamEvent {

    private String type;

    private String content;

    private String message;

    public AgentStreamEvent() {
    }

    public AgentStreamEvent(String type, String content) {
        this.type = type;
        this.content = content;
    }

    public static AgentStreamEvent error(String message) {
        AgentStreamEvent event = new AgentStreamEvent();
        event.setType("error");
        event.setContent(message);
        event.setMessage(message);
        return event;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
