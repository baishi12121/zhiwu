package com.hyf.mallseckillservice.service;

import org.springframework.boot.ApplicationArguments;

public interface SeckillTask {

    public void run(ApplicationArguments args);
    public void warmUpActiveItems();
    public void retryPendingMessages();
    public void cancelExpiredOrders();
    public void recoverOrphanInflightDeducts();

}
