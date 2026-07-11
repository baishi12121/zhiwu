package com.hyf.mallcommon.redis.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.mallcommon.core.constant.MallConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 便捷封装 —— 包装 {@link RedisTemplate} 五大数据类型 + 计数器。
 *
 * <p>所有 key 自动带 {@link MallConstants#REDIS_PREFIX}（{@code mall:}）；
 * 已含前缀的 key 不重复拼接。Value 使用 Jackson JSON 序列化（带类型信息，
 * 读取时传入 {@code Class<T>} 推断）。
 *
 * <p>方法按数据类型分前缀命名：{@code str*}（String）、{@code hash*}（Hash）、
 * {@code list*}、{@code set*}、{@code zset*}，与 Redis 命令语义一一对应。
 *
 * <p>JSON 序列化异常不抛 {@link BizException}——业务应能容错缓存不可用，
 * 本类仅记 warn 日志并返回空值。
 *
 * @author hyf
 */
@Slf4j
public class RedisUtils {

    private final RedisTemplate<String, Object> redis;
    private final StringRedisTemplate stringRedis;
    private static final String PREFIX = MallConstants.REDIS_PREFIX; // "mall:"

    public RedisUtils(RedisTemplate<String, Object> redisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.redis = redisTemplate;
        this.stringRedis = stringRedisTemplate;
    }

    // ==================== String 操作 ====================

    /** SET key value（无过期时间） */
    public void strSet(String key, Object value) {
        redis.opsForValue().set(prefixKey(key), value);
    }

    /** SETEX key seconds value */
    public void strSet(String key, Object value, long seconds) {
        redis.opsForValue().set(prefixKey(key), value, Duration.ofSeconds(seconds));
    }

    /** SETEX key duration value */
    public void strSet(String key, Object value, Duration ttl) {
        redis.opsForValue().set(prefixKey(key), value, ttl);
    }

    /**
     * GET key，反序列化为指定类型。
     *
     * <p>处理不了的类型（缓存缺失 / 序列化异常与类版本不匹配）返回 {@code null}，
     * 不中断业务流程——调用方自行降级查库。
     */
    @SuppressWarnings("unchecked")
    public <T> T strGet(String key, Class<T> type) {
        Object val = redis.opsForValue().get(prefixKey(key));
        if (val == null) {
            return null;
        }
        try {
            // GenericJackson2JsonRedisSerializer 存储的是 LinkedHashMap，需用 mapper 做二次转换
            ObjectMapper mapper = JacksonHolder.MAPPER;
            String json = mapper.writeValueAsString(val);
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("[redis] 反序列化失败 key={} type={}", key, type.getSimpleName(), e);
            return null;
        }
    }

    /** DEL key */
    public Boolean strDel(String key) {
        return redis.delete(prefixKey(key));
    }

    /** EXISTS key */
    public Boolean strExists(String key) {
        return redis.hasKey(prefixKey(key));
    }

    /** EXPIRE key seconds */
    public Boolean strExpire(String key, long seconds) {
        return redis.expire(prefixKey(key), Duration.ofSeconds(seconds));
    }

    /** TTL key，返回 -1 永不过期，-2 key 不存在 */
    public Long strGetExpire(String key) {
        return redis.getExpire(prefixKey(key));
    }

    // ==================== 计数器 ====================

    /** INCR key */
    public Long increment(String key) {
        return redis.opsForValue().increment(prefixKey(key));
    }

    /** INCRBY key delta */
    public Long increment(String key, long delta) {
        return redis.opsForValue().increment(prefixKey(key), delta);
    }

    /** DECR key */
    public Long decrement(String key) {
        return redis.opsForValue().decrement(prefixKey(key));
    }

    /** DECRBY key delta */
    public Long decrement(String key, long delta) {
        return redis.opsForValue().decrement(prefixKey(key), delta);
    }

    // ==================== Hash 操作 ====================

    /** HSET key field value */
    public void hashPut(String key, String field, Object value) {
        redis.opsForHash().put(prefixKey(key), field, value);
    }

    /** HSET key field value map（批量） */
    public void hashPutAll(String key, Map<String, Object> entries) {
        redis.opsForHash().putAll(prefixKey(key), entries);
    }

    /** HGET key field */
    @SuppressWarnings("unchecked")
    public <T> T hashGet(String key, String field, Class<T> type) {
        Object o = redis.opsForHash().get(prefixKey(key), field);
        if (o == null) return null;
        try {
            ObjectMapper mapper = JacksonHolder.MAPPER;
            String json = mapper.writeValueAsString(o);
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("[redis] hash 反序列化失败 key={} field={}", key, field, e);
            return null;
        }
    }

    /** HGET key field 返回 String */
    public String hashGetString(String key, String field) {
        Object o = redis.opsForHash().get(prefixKey(key), field);
        return o != null ? String.valueOf(o) : null;
    }

    /** HDEL key field [fields...] */
    public void hashDel(String key, String... fields) {
        if (fields.length == 1) {
            redis.opsForHash().delete(prefixKey(key), (Object) fields[0]);
        } else {
            redis.opsForHash().delete(prefixKey(key), (Object[]) fields);
        }
    }

    /** HEXISTS key field */
    public Boolean hashHasKey(String key, String field) {
        return redis.opsForHash().hasKey(prefixKey(key), field);
    }

    /** HGETALL key */
    public Map<Object, Object> hashEntries(String key) {
        return redis.opsForHash().entries(prefixKey(key));
    }

    // ==================== List 操作 ====================

    /** RPUSH key value */
    public Long listPush(String key, Object value) {
        return redis.opsForList().rightPush(prefixKey(key), value);
    }

    /** RPUSH key values... */
    public Long listPushAll(String key, Collection<?> values) {
        return redis.opsForList().rightPushAll(prefixKey(key), values.toArray());
    }

    /** LPOP key */
    @SuppressWarnings("unchecked")
    public <T> T listPop(String key, Class<T> type) {
        Object o = redis.opsForList().leftPop(prefixKey(key));
        return cast(o, type, key);
    }

    /** RPOP key */
    @SuppressWarnings("unchecked")
    public <T> T listRightPop(String key, Class<T> type) {
        Object o = redis.opsForList().rightPop(prefixKey(key));
        return cast(o, type, key);
    }

    /** LRANGE key start end */
    public <T> List<T> listRange(String key, long start, long end, Class<T> type) {
        List<Object> list = redis.opsForList().range(prefixKey(key), start, end);
        return castList(list, type, key);
    }

    /** LLEN key */
    public Long listSize(String key) {
        return redis.opsForList().size(prefixKey(key));
    }

    // ==================== Set 操作 ====================

    /** SADD key member [members...] */
    public Long setAdd(String key, Object... values) {
        return redis.opsForSet().add(prefixKey(key), values);
    }

    /** SMEMBERS key */
    public <T> Set<T> setMembers(String key, Class<T> type) {
        Set<Object> members = redis.opsForSet().members(prefixKey(key));
        if (members == null || members.isEmpty()) return Collections.emptySet();
        ObjectMapper mapper = JacksonHolder.MAPPER;
        Set<T> result = new java.util.LinkedHashSet<>();
        for (Object o : members) {
            try {
                String json = mapper.writeValueAsString(o);
                result.add(mapper.readValue(json, type));
            } catch (JsonProcessingException e) {
                log.warn("[redis] set 反序列化失败 key={}", key, e);
            }
        }
        return result;
    }

    /** SISMEMBER key member */
    public Boolean setIsMember(String key, Object value) {
        return redis.opsForSet().isMember(prefixKey(key), value);
    }

    /** SREM key member [members...] */
    public Long setRemove(String key, Object... values) {
        return redis.opsForSet().remove(prefixKey(key), (Object[]) values);
    }

    /** SCARD key */
    public Long setSize(String key) {
        return redis.opsForSet().size(prefixKey(key));
    }

    // ==================== ZSet 操作 ====================

    /** ZADD key score member */
    public Boolean zsetAdd(String key, Object value, double score) {
        return redis.opsForZSet().add(prefixKey(key), value, score);
    }

    /** ZRANGE key start end（按 score 升序） */
    public <T> Set<T> zsetRange(String key, long start, long end, Class<T> type) {
        Set<Object> set = redis.opsForZSet().range(prefixKey(key), start, end);
        return castSet(set, type, key);
    }

    /** ZREVRANGE key start end（按 score 降序） */
    public <T> Set<T> zsetReverseRange(String key, long start, long end, Class<T> type) {
        Set<Object> set = redis.opsForZSet().reverseRange(prefixKey(key), start, end);
        return castSet(set, type, key);
    }

    /** ZCARD key */
    public Long zsetSize(String key) {
        return redis.opsForZSet().size(prefixKey(key));
    }

    /** ZSCORE key member */
    public Double zsetScore(String key, Object value) {
        return redis.opsForZSet().score(prefixKey(key), value);
    }

    /** ZINCRBY key increment member */
    public Double zsetIncrement(String key, Object value, double delta) {
        return redis.opsForZSet().incrementScore(prefixKey(key), value, delta);
    }

    /** ZREM key member [members...] */
    public Long zsetRemove(String key, Object... values) {
        return redis.opsForZSet().remove(prefixKey(key), values);
    }

    // ==================== 通用 ====================

    /** DEL key */
    public Boolean delete(String key) {
        return redis.delete(prefixKey(key));
    }

    /** DEL keys... */
    public Long delete(Collection<String> keys) {
        List<String> prefixed = new ArrayList<>(keys.size());
        for (String k : keys) prefixed.add(prefixKey(k));
        return redis.delete(prefixed);
    }

    /** EXISTS key */
    public Boolean hasKey(String key) {
        return redis.hasKey(prefixKey(key));
    }

    /** KEYS pattern（生产慎用，仅建议在运维/调试场景） */
    public Set<String> keys(String pattern) {
        return redis.keys(prefixKey(pattern));
    }

    // ==================== String 直连（不带 JSON 序列化） ====================

    /** SET 纯文本 value */
    public void stringSet(String key, String value) {
        stringRedis.opsForValue().set(prefixKey(key), value);
    }

    /** SETEX 纯文本 */
    public void stringSet(String key, String value, long seconds) {
        stringRedis.opsForValue().set(prefixKey(key), value, seconds, TimeUnit.SECONDS);
    }

    /** GET 纯文本 */
    public String stringGet(String key) {
        return stringRedis.opsForValue().get(prefixKey(key));
    }

    // ==================== 内部 ====================

    /** 为 key 加上统一前缀 "mall:"（已含则不加） */
    private String prefixKey(String key) {
        if (key == null) return null;
        return key.startsWith(PREFIX) ? key : PREFIX + key;
    }

    @SuppressWarnings("unchecked")
    private <T> T cast(Object o, Class<T> type, String key) {
        if (o == null) return null;
        try {
            ObjectMapper mapper = JacksonHolder.MAPPER;
            String json = mapper.writeValueAsString(o);
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("[redis] 反序列化失败 key={}", key, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(List<Object> list, Class<T> type, String key) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        List<T> result = new ArrayList<>(list.size());
        ObjectMapper mapper = JacksonHolder.MAPPER;
        for (Object o : list) {
            try {
                String json = mapper.writeValueAsString(o);
                result.add(mapper.readValue(json, type));
            } catch (JsonProcessingException e) {
                log.warn("[redis] list 反序列化失败 key={}", key, e);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> Set<T> castSet(Set<Object> set, Class<T> type, String key) {
        if (set == null || set.isEmpty()) return Collections.emptySet();
        Set<T> result = new java.util.LinkedHashSet<>();
        ObjectMapper mapper = JacksonHolder.MAPPER;
        for (Object o : set) {
            try {
                String json = mapper.writeValueAsString(o);
                result.add(mapper.readValue(json, type));
            } catch (JsonProcessingException e) {
                log.warn("[redis] 反序列化失败 key={}", key, e);
            }
        }
        return result;
    }

    /**
     * ObjectMapper 持有器 —— 复用与 Redis 配置一致的序列化规则（JavaTime 模块 + 类型信息）。
     * 不创建新的实例，避免与 RedisConfig 配置偏离。
     */
    static class JacksonHolder {
        static final ObjectMapper MAPPER;
        static {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.ALL,
                    com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
            mapper.activateDefaultTyping(
                    com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator.instance,
                    com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL);
            MAPPER = mapper;
        }
    }
}
