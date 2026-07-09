package com.hyf.mallorderservice.api;

import com.hyf.mallorderservice.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "mall-product-service")
public interface ProductClient {
    @PostMapping("/products/decrease-stock")
    Result<String> decreaseStock(@RequestParam("id") Long id, @RequestParam("count") Integer count);

}
