package com.hyf.mallaiservice.dto;

/**
 * shopkeeper-agent /api/query 接口请求体
 *
 * 对应 Python 侧 QuerySchema，只需一个 query 字段
 *
 * @author hyf
 */
public class AgentQueryRequest {

    private String query;

    public AgentQueryRequest() {
    }

    public AgentQueryRequest(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
