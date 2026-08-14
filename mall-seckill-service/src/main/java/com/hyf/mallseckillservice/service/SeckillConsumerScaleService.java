package com.hyf.mallseckillservice.service;

import java.util.Map;

public interface SeckillConsumerScaleService {

    Map<String, Object> scaleTo(int concurrency);

    Map<String, Object> inspectAndScale();
}
