package com.hyf.mallseckillservice;

import com.hyf.mallseckillservice.dto.ExecuteReqDTO;
import com.hyf.mallseckillservice.service.SeckillApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * 诊断测试：直接调用 execute 捕获异常堆栈，定位 /seckill/{id}/execute 返回 500 的根因。
 * 临时文件，定位后删除。
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class SeckillDiagTest {

    @Autowired
    SeckillApplicationService seckillApplicationService;

    @Test
    void diagExecute() {
        ExecuteReqDTO req = new ExecuteReqDTO();
        req.setSeckillItemId(1L);
        req.setQuantity(1);
        req.setAddressId(1L);
        try {
            System.out.println("DIAG OK: " + seckillApplicationService.execute(2000000L, 1L, req));
        } catch (Exception e) {
            System.out.println("DIAG EXCEPTION: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
            for (Throwable c = e.getCause(); c != null; c = c.getCause()) {
                System.out.println("CAUSED BY: " + c.getClass().getName() + ": " + c.getMessage());
                c.printStackTrace(System.out);
            }
        }
    }
}
