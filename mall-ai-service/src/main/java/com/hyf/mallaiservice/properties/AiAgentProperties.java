package com.hyf.mallaiservice.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智能客服 Agent 配置属性
 *
 * 对应 application.yml 中的 mall.ai.agent 配置项
 * 指向 shopkeeper-agent Python 服务的 SSE 接口
 *
 * @author hyf
 */
@Component
@ConfigurationProperties(prefix = "mall.ai.agent")
public class AiAgentProperties {

    /** Python Agent 服务基础地址 */
    private String baseUrl = "http://localhost:8090";

    /** SSE 查询接口路径 */
    private String queryPath = "/api/query";

    /** 单次问答超时时间（毫秒） */
    private long timeoutMs = 60000L;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getQueryPath() {
        return queryPath;
    }

    public void setQueryPath(String queryPath) {
        this.queryPath = queryPath;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
