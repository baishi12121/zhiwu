package com.hyf.mallcommon.redis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hyf.mallcommon.redis.utils.RedisLock;
import com.hyf.mallcommon.redis.utils.RedisUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 自动装配。
 *
 * <p>通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册（同 mall-common-security / mall-common-mybatis / mall-common-oss 模式），
 * 保证本 jar 的包名 {@code com.hyf.mallcommon.redis} 不在各业务服务 base package 下时仍能装配。
 *
 * <p>装配内容：
 * <ol>
 *   <li>{@link RedisTemplate}{@code <String,Object>}：key String 序列化，value Jackson JSON（带类型信息）；</li>
 *   <li>{@link StringRedisTemplate}：纯字符串操作，直接可注入；</li>
 *   <li>{@link RedisUtils}：五大数据类型 + 计数器 + Key 自动前缀的便捷封装；</li>
 *   <li>{@link RedisLock}：基于 SET NX + Lua 脚本的分布式锁。</li>
 * </ol>
 *
 * @author hyf
 */
@AutoConfiguration
public class RedisAutoConfiguration {

    /**
     * RedisTemplate（key 用 String，value 用 Jackson JSON 带类型信息）。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * StringRedisTemplate：纯文本操作（锁、计数器、简单标记）。
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Redis 便捷封装 —— 对 RedisTemplate 做类型化包装，key 自动带 mall: 前缀。
     */
    @Bean
    public RedisUtils redisUtils(RedisTemplate<String, Object> redisTemplate,
                                  StringRedisTemplate stringRedisTemplate) {
        return new RedisUtils(redisTemplate, stringRedisTemplate);
    }

    /**
     * 分布式锁 —— 基于 SET NX + Lua 原子释放。
     */
    @Bean
    public RedisLock redisLock(StringRedisTemplate stringRedisTemplate) {
        return new RedisLock(stringRedisTemplate);
    }
}
