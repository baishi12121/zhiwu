package com.hyf.mallcouponservice.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {

    //使用Lua脚本，实现查库存，查重复，扣库存

    @Bean
    public DefaultRedisScript<Long> seckillScript(){
        DefaultRedisScript<Long> script=new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                //KEYS[1]：库存对应的 Redis key,KEYS[2]：记录已秒杀成功用户的 Set 集合 key
                //ARGV[1]：当前参与秒杀的用户标识（如用户 ID）

                //获取并检查库存
                "local stock = tonumber(redis.call('get', KEYS[1])) " +
                "if stock == nil or stock <= 0 then return 0 end " +           // 0: 库存不足
                        //检查用户是否已经秒杀成功
                        "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then return 1 end " +  // 1: 重复抢券
                        //扣减库存
                        "redis.call('decr', KEYS[1]) " +
                        //记录成功用户
                        "redis.call('sadd', KEYS[2], ARGV[1]) " +
                        "return 2");                                                   // 2: 抢券成功

        return script;
    }
}
