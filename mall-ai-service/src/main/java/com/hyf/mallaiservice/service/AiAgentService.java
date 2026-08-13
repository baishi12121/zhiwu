package com.hyf.mallaiservice.service;

import reactor.core.publisher.Flux;

public interface AiAgentService {

    public Flux<String> chat(String query);
    public boolean healthCheck();

}
