package com.hyf.mallseckillservice;

import com.hyf.mallcommon.security.jwt.JwtTokenService;
import com.hyf.mallcommon.security.jwt.LoginUser;
import com.hyf.mallcommon.security.jwt.TokenPair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 诊断测试：经完整 HTTP 链路（拦截器 + controller）调 execute，复现 IDEA 服务的 500。
 * 临时文件，定位后删除。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class SeckillHttpDiagTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JwtTokenService jwtTokenService;

    @Test
    void diagHttp() {
        LoginUser u = LoginUser.builder()
                .userId(2000001L).nickname("t").avatar("")
                .memberLevel("NORMAL").client("miniapp").build();
        TokenPair pair = jwtTokenService.createTokenPair(u);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(pair.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("seckillItemId", 1, "quantity", 1, "addressId", 1);
        ResponseEntity<String> r = rest.exchange("/seckill/1/execute", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        System.out.println("HTTP DIAG STATUS: " + r.getStatusCode());
        System.out.println("HTTP DIAG BODY: " + r.getBody());
    }
}
