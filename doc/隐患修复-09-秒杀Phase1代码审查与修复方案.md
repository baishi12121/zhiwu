# 隐患修复-09：秒杀 Phase 1 代码审查与修复方案

> 关联：`秒杀方案分阶段实施计划.md`（Phase 1 集中式实现）、`隐患修复-05`（SKU 维度统一）、`隐患修复-06`（秒杀闭环）
> 审查对象：`mall-seckill-service`（8089）及协同点（`mall-gateway-service`、`mall-common-security`、`mall-order-service`）
> 审查日期：2026-08-13 ｜ 审查分支：`codex/seckill-phase1`
> 状态：**仅输出修复方案，未改动任何代码**

---

## 1. 结论摘要

Phase 1 骨架（预热 → 入口 → 本地消息表 → Lua 扣库存 → MQ 建单 → 状态机回写 → 延迟/定时取消 → 回补）已完整搭建，工程结构与方案 §3.4.5 基本对齐。但存在 **2 个 Critical、4 个 Important、若干 Minor** 缺陷，直接影响验收项：

| 验收项 | 当前预判 | 阻塞问题 |
|---|---|---|
| 无超卖（Redis/DB 一致） | ❌ | P2（warmUp 复活库存）、P4（消费失败不回补 Redis） |
| 无丢消息 | ❌ | P4（nack 丢消息 + mq_message 孤儿） |
| 无重复订单 | ✅ | DB `uk_user_activity_item` + Redis 状态机兜底 |
| 500 QPS / P99<800ms | ⚠️ 需修复后压测 | P2/P4 引发的失败风暴与重试会拖垮 P99 |
| 进程 kill 不丢消息 | ❌ | P4 + mq_message 状态孤儿 |
| 支付超时取消 + 回补 | ⚠️ 部分通过 | P6（回补双写非原子） |
| 安全（防黄牛/伪造用户） | ❌ | P1（X-User-Id 可伪造） |

**修复优先级**：P1 → P2 → P3 → P4 → P5 → P6 → P9 → P7 → P8 → P10。

---

## 2. 问题与修复详表

| 编号 | 问题 | 严重度 | 涉及文件 |
|---|---|---|---|
| P1 | `X-User-Id` 可伪造，自校验形同虚设 | Critical | SeckillController、AuthGlobalFilter、SeckillOrderService |
| P2 | 定时 warmUp 无条件覆盖 Redis 库存 | Critical | SeckillTask、SeckillApplicationService、SeckillStockRedis |
| P3 | mq_message 状态机倒退（4→2） | Important | MqMessageMapper.xml、SeckillMqConfig |
| P4 | 消费失败丢消息 + 不回补 + 状态孤儿（合并 I1/I2） | Important | SeckillOrderConsumer、SeckillMqConfig |
| P5 | retryExpired 重发"刚发送"的消息，retry_count 误累加 | Important | MqMessageService、MqMessageMapper.xml |
| P6 | 回补 Redis/DB 双写非原子 | Important | SeckillCompensateService |
| P7 | `StockCompensateDTO` 死参数、契约不一致 | Minor | InternalSeckillController、SeckillCompensateService |
| P8 | Redis `database: 6` 与全局 `1` 不一致 | Minor | application.yml |
| P9 | execute 重复入口遇 status=3 卡死 | Minor | SeckillApplicationService |
| P10 | 次要项（M2/M3/M5/M6） | Minor | 多处 |

> 原审查报告中 M1（Lua `INCRBY`→`EXPIRE` 崩溃窗口）经复核为**误报**：整个扣减脚本在 Redis 端原子执行，不存在进程中断窗口，已从本文移除。详见 §2.11。

---

## 3. 逐条修复方案

### P1【Critical】`X-User-Id` 可伪造

**现象/危害**

任意持有合法 token 的用户，通过绕过网关直连 `:8089`（或经网关）携带伪造的 `X-User-Id` 头，即可代替他人秒杀、占用他人限购额度、以他人身份绑定任意 addressId 下单。这是黄牛最喜欢的绕过点。

**根因**

- `SeckillController.java:20,27`：`execute`/`result` 用 `@RequestHeader("X-User-Id") Long userId` 直接取请求头。
- `TokenAuthInterceptor.java:63-69`：只校验 token 并把 `LoginUser` 写入 `SecurityContextHolder`，**不覆写请求头**。
- `SecurityAutoConfiguration.java:120-123`：拦截器拦截 `/**`，白名单仅 `/internal/**`，`/seckill/**` 已校验 token（因此持有效 token 即通过，匿名攻击被挡住，但"身份绑定"未建立）。
- `AuthGlobalFilter.java:106-109`：网关用 `request.mutate().header(...)` 是**追加**语义，不清除客户端自带同名头。

**修复（三步，缺一不可）**

1. **controller 不再信任 header，改从 `SecurityContextHolder` 取 userId**：

```java
// SeckillController.execute / result
LoginUser user = SecurityContextHolder.get();
if (user == null || user.getUserId() == null) {
    throw new UnauthorizedException("未登录");
}
Long userId = user.getUserId();
```

去掉 `@RequestHeader("X-User-Id")` 参数。`LoginUser` 提供 `getUserId()`（`mall-common-security/jwt/LoginUser.java`）。

2. **网关兜底**：先 remove 客户端伪造的同名头，再 set 真实值：

```java
// AuthGlobalFilter.filter()
ServerHttpRequest mutated = request.mutate()
        .headers(h -> {
            h.remove("X-User-Id");
            h.remove("X-User-Nickname");
        })
        .header("X-User-Id", userId != null ? userId : "")
        .header("X-User-Nickname", nickname != null ? nickname : "")
        .build();
```

3. **顺带收紧地址校验**（见 P10-M6）：`SeckillOrderService.fillAddress` 中 `address == null` 时抛 `BizException` 而非兜底建单，防止伪造 addressId。

**验收**：持 A 的 token + `X-User-Id: B` 直连 8089 调用 execute，服务端 userId 必须解析为 A；日志可见使用 `SecurityContextHolder` 值。

---

### P2【Critical】定时 warmUp 无条件覆盖 Redis 库存

**现象/危害**

活动进行中，Redis 库存每 60s 被重置为 DB 当前值，导致"execute 已扣减但消费者尚未扣 DB"的在途预约被反复复活，Redis 允许通过的秒杀数超过 DB 实库存 → 大量请求 Redis 通过、DB 消费失败 → 失败风暴 + 库存泄漏，破坏"无超卖"与 500 QPS 验收。

**根因**

- `SeckillTask.java:27-30`：`@Scheduled(fixedDelay = 60_000L)` 每 60s 调 `warmUp()`。
- `SeckillStockRedis.java:57-59`：`warmUpStock` 用 `set()` **无条件覆盖**。
- 库存两条扣减时点不一致：Redis 在 execute 的 Lua 扣减（`SeckillApplicationService.java:86-92`），DB 只在消费建单扣减（`SeckillOrderService.java:87`），存在 in-flight 窗口。
- DB 侧 `SeckillItemMapper.xml:14-19` 的 `AND seckill_stock >= #{quantity}` 能防止商品真超卖，但无法阻止上述失败风暴。

**修复**

1. `warmUpStock` 改为仅在 Key 不存在时写入（`SET NX`），**绝不覆盖在途扣减**：

```java
// SeckillStockRedis
public void warmUpStock(Long activityId, Long seckillItemId, int stock) {
    stringRedisTemplate.opsForValue().setIfAbsent(
            SeckillConstants.stockKey(activityId, seckillItemId), String.valueOf(stock));
}
```

2. 周期任务只刷新 itemMeta（TTL 续期），不再触碰 stock：

```java
// SeckillTask
@Scheduled(fixedDelay = 60_000L)
public void refreshActiveItemMeta() {
    for (SeckillActivityDO activity : seckillActivityMapper.selectActiveActivities()) {
        for (SeckillItemDO item : seckillItemMapper.selectEnabledByActivityId(activity.getId())) {
            seckillStockRedis.cacheItemMeta(item.getId(),
                    seckillApplicationService.toMetaJson(activity.getId(), item),
                    seckillApplicationService.ttlSeconds(activity));
        }
    }
}
```

库存预热仅保留启动时的 `ApplicationRunner`（`SeckillTask.run`）一次。若需要"活动开始前批量预热"，由管理端/运维脚本在开始前调用一次。

**残留风险（Phase 1 接受并记录）**：活动进行中若 Redis 库存 Key 被逐出（LFU/重启），`setIfAbsent` 不会重建，需人工重预热。可在补偿任务里加"Key 缺失且无在途订单时重建"逻辑，留给 Phase 2 对账。

**验收**：活动进行中制造消费滞后，触发一次 warmUp，断言 Redis 库存**不回升**。

---

### P3【Important】mq_message 状态机倒退

**现象/危害**

broker 的 publisher confirm ACK 晚于消费完成到达时，`markSent`（status=2）会把已完成的 `markDone`（status=4）覆盖回去。订单已建且已支付，但 `result` 接口永久返回 `pending`。每次 retryExpired 重发都会再触发一次 confirm 回调，加剧该竞态。

**根因**

- `MqMessageMapper.xml:6-10`：`UPDATE mq_message SET status=#{status} WHERE message_id=#{messageId}` 为**无条件 UPDATE**。
- `SeckillMqConfig.java:43-47`：ACK 回调调 `markSent`；`SeckillOrderService.java:91`：消费建单调 `markDone`。两者时序不定。

**修复**

让状态机单调前进——UPDATE 加"只允许向前"条件：

```xml
<!-- MqMessageMapper.xml -->
<update id="updateStatusByMessageId">
    UPDATE mq_message
    SET status = #{status}
    WHERE message_id = #{messageId}
      AND status &lt; #{status>
</update>
```

一次改动覆盖 `markSending`/`markSent`/`markFailed`/`markDone` 全部调用。

**验收**：模拟"ACK 晚于消费完成"，断言 status 停留在 4。

---

### P4【Important】消费失败丢消息 + 不回补 + 状态孤儿（合并 I1/I2）

**现象/危害**

- `x-delivery-limit:3` 加在 **classic durable 队列**上（该参数仅 quorum 队列生效，被 RabbitMQ 忽略），"重投上限 3"形同虚设。
- 消费失败走 `basicNack(requeue=false)` 且未配置 DLX → 消息从 MQ 消失。
- catch 分支**未回补 Redis 库存**（库存泄漏）、**未更新 mq_message**（停在 status=2，`retryExpired` 只扫 status=1 不重投）→ 用户 `result` 永久 `pending`。

**根因**

- `SeckillMqConfig.java:27-30`：classic Queue 带 `x-delivery-limit`。
- `SeckillOrderConsumer.java:61-68`：catch 只 `set FAILED` + log + `basicNack(false)`；消费者未注入 `MqMessageService`/`SeckillStockRedis`。

**修复（重试交给 DB 状态机，MQ 仅作传输通道）**

1. 消费者 catch 分支补齐三件事：

```java
// SeckillOrderConsumer —— 注入 SeckillStockRedis + MqMessageService
} catch (Exception e) {
    // 1) 回补 Redis 库存
    seckillStockRedis.restoreStock(dto.getActivityId(), dto.getSeckillItemId(), dto.getQuantity());
    // 2) 更新本地消息表（失败，供 result 查询与对账）
    mqMessageService.markFailed(dto.getMessageId());
    // 3) ACK 丢弃该条；DB 状态机负责重投/人工介入
    channel.basicAck(deliveryTag, false);
    log.error("[seckill-consumer] create order failed, messageId={}", dto.getMessageId(), e);
}
```

> 若希望自动重试而非一次性失败，可改为 `markSending(messageId)` 复位 status=1，让 `retryExpired`（受 P5 修复保护，有 60s 宽限期）兜底重投；DB 侧 `retry_count` 天然限次。

2. **回补幂等**：消费失败路径的 `restoreStock` 需自己的幂等 key（如 `seckill:consumer-restore:{messageId}`，`setIfAbsent`），防止同一消息重投时多次回补。若走"复位 status=1 交给 retryExpired"方案，则由 `SeckillCompensateService.restoreStockOnce` 既有 `setIfAbsent` 兜底，无需新增。

3. **去掉无效队列参数**：删除 `SeckillMqConfig.java:29` 的 `Map.of("x-delivery-limit", 3)`。若确需 broker 侧重投上限，将队列改为 quorum（`new Queue(name, true, false, false, Map.of("x-queue-type","quorum","x-delivery-limit",3))`，需 RabbitMQ ≥ 3.8）。

**验收**：注入 `deductStock` 返回 0 / DB 异常，断言①Redis 库存回补②mq_message 置 failed③`result` 返回 failed④MQ 无残留消息。

---

### P5【Important】retryExpired 重发"刚发送"的消息

**现象/危害**

execute 刚 `markSending`（status=1）的消息，`next_retry_time` 仍为 NULL，30s 后 `retryExpired` 立即重发 → 重复 MQ 消息 + `retry_count` 无谓累加。若 broker ACK 持续延迟 >90s，`retry_count` 累到 3 被误判 `markFailed`，正常秒杀被标失败。

**根因**

- `MqMessageMapper.xml:19-26`：`status=1 AND (next_retry_time IS NULL OR next_retry_time <= NOW())`。
- `init.sql:862`：`next_retry_time DATETIME DEFAULT NULL`。
- `MqMessageService.java:46-48`：`markSending` 只置 status，不写 `next_retry_time`。

**修复**

`markSending` 时写入 60s 宽限期，区分"刚发送"与"可重试"：

```java
// MqMessageService
public void markSending(String messageId) {
    mqMessageMapper.updateStatusByMessageId(messageId, SeckillConstants.MSG_PENDING_SEND);
    mqMessageMapper.markRetry(messageId, 0, LocalDateTime.now().plusSeconds(60));
}
```

（`markRetry` 已存在：`MqMessageMapper.xml:12-17`。）

**验收**：发送后立即查询 `next_retry_time`，断言 ≥ 当前时间 + 60s；30s 内不被重发。

---

### P6【Important】回补 Redis/DB 双写非原子

**现象/危害**

`@Transactional` 只覆盖 DB `restoreStock`，Redis `increment` 在事务提交前执行。Redis 写成功而事务回滚时库存多回补；Redis 写失败而 DB 提交时库存少回补（泄漏）。`restoreKey` 的 `setIfAbsent` 只防同单重复，不防跨系统偏差。（后续已落地：Redis 回补移到事务提交后 afterCommit，且幂等从 Redis `restoreKey` 迁到 DB 唯一键 `uk_message_id`，见「秒杀方案分阶段实施计划」4.4.3。）

**根因**：`SeckillCompensateService.java:68-83`，`restoreStockOnce` 内 DB/Redis 混合。

**修复**

Redis 回补移到事务提交后：

```java
// SeckillCompensateService.restoreStockOnce
OrderItemDO item = orderItemMapper.selectFirstByOrderId(order.getId());
int quantity = item == null || item.getQuantity() == null ? 1 : item.getQuantity();
seckillItemMapper.restoreStock(order.getSeckillItemId(), quantity);   // 事务内（DB）

if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() {
            seckillStockRedis.restoreStock(order.getActivityId(), order.getSeckillItemId(), quantity);
        }
    });
} else {
    seckillStockRedis.restoreStock(order.getActivityId(), order.getSeckillItemId(), quantity);
}
```

**残留风险**：`afterCommit` 后 Redis 写失败仍会偏差，Phase 1 记录日志即可，最终一致性由 Phase 2 对账兜底。

**验收**：注入事务回滚（如 DB 抛异常），断言 Redis 库存不被回补。

---

### P7【Minor】`StockCompensateDTO` 死参数、契约不一致

**现象**

order-service 按方案契约传 `{activityId, seckillItemId, userId, quantity}`（`OrderApplicationService.java:519-524`），seckill-service 完全忽略（`InternalSeckillController.java:17-22`），只凭 orderNo 反查 `order` 表。功能结果正确（DB 是唯一事实源），但契约描述与实际不符，且无防御性校验。

**修复**（二选一）

1. **用 DTO 做交叉校验**（推荐）：
```java
// SeckillCompensateService.restoreForCancel
if (dto != null
        && (!order.getActivityId().equals(dto.getActivityId())
            || !order.getSeckillItemId().equals(dto.getSeckillItemId())
            || !order.getUserId().equals(dto.getUserId()))) {
    log.error("[seckill-compensate] DTO mismatch order, orderNo={}, dto={}", order.getOrderNo(), dto);
    throw new BizException(ResultCode.BAD_REQUEST.getCode(), "回补参数与订单不符");
}
```
2. 删除 `@RequestBody` 参数，并同步修订 `秒杀方案分阶段实施计划.md` §3.4.4 的内部回补契约描述。

---

### P8【Minor】Redis `database: 6` 与全局 `1` 不一致

**现象**

`application.yml:25` 配置 `spring.data.redis.database: 6`，其余服务（含 auth 写入 token 黑名单）用 `1`。`mall-common-redis` 不强制 DB，跟随各服务 yml。后果：`TokenAuthInterceptor.isBlacklisted`（`TokenAuthInterceptor.java:93-100`）在 seckill 侧查 DB 6 的黑名单，而 logout 写的是 DB 1 → **登出 token 在 seckill 侧仍有效**（至 30min 过期）。

**修复**

- 若秒杀数据无隔离强需求：统一为 `database: 1`，与其他服务一致。
- 若刻意隔离秒杀热数据：在 `application.yml` 注明原因，且 seckill 关闭黑名单检查（`mall.security` 白名单无法排除单方法，需在 `isBlacklisted` 加开关或直接信任网关已校验），并同步 CLAUDE.md 基础环境说明。

---

### P9【Minor】execute 重复入口遇 status=3 卡死

**现象**

`SeckillApplicationService.java:77-84`：`DuplicateKeyException` 时 status≠4 一律返回 `"queued"`。若历史记录卡在 status=3（发送失败），用户重试只会拿到 `queued` 死路（`retryExpired` 不扫 3，`result` 返回 failed，但库存已被扣）。

**修复**

status=3 时走"重新发送"而非返回 queued：

```java
// SeckillApplicationService.execute — DuplicateKeyException 分支
if (existing != null && existing.getStatus() != null) {
    if (existing.getStatus() == SeckillConstants.MSG_DONE) {
        throw new BizException(LIMIT_HIT_CODE, "您已购买过该商品");
    }
    if (existing.getStatus() == SeckillConstants.MSG_SEND_FAILED) {
        mqMessageService.markSending(messageId);
        mqMessageService.sendOrderMessage(message);
        return new ExecuteResultDTO("queued", messageId);
    }
}
return new ExecuteResultDTO("queued", messageId);
```

---

### P10【Minor】次要项

| 项 | 位置 | 问题 | 修复 |
|---|---|---|---|
| M2 | `SeckillOrderConsumer.java:31-47` | commit 成功但 set SUCCESS 前崩溃 → 30min（TTL）内重投循环 | 命中 PROCESSING 时检查 key 剩余 TTL：剩余过短则继续 requeue 等待，否则查 `order` 表确认已建单则直接置 SUCCESS + ACK |
| M3 | `SeckillOrderConsumer.java:26-27` | 消息体反序列化失败抛在方法外，走默认 `requeue=true` → 毒消息热循环 | 为 `@RabbitListener` 配置 `errorHandler`（或 `MessageRecoverer`），N 次后拒绝/丢弃；或入参改 `Message` 手动反序列化并 try-catch |
| M5 | `SeckillApplicationService.execute` | 无事务边界；`tryDeduct` 成功后 `markSending` 抛 DB 异常 → Redis 已扣、mq_message 卡 status=0（retryExpired 不扫） | 对 `markSending` 包 try-catch，失败时 `restoreStock` 回滚 Redis；或把 `markSending` 前移进 try 且失败路径统一回滚 |
| M6 | `SeckillOrderService.java:50,119-139` | `address == null` 时兜底建单而非报错 | `address == null` 抛 `BizException("收货地址无效")`；随 P1 一起修复后此风险收敛 |

---

## 4. 已撤销疑点说明

原审查报告 M1「Lua `DECRBY → INCRBY → EXPIRE` 存在进程崩溃窗口、限购 key 可能无 TTL 永驻」经复核**不成立**：

- `SeckillStockRedis.java:16-34` 整个扣减脚本由 `stringRedisTemplate.execute(RedisScript, ...)` 在 **Redis 服务端原子执行**，脚本内部不存在可观察的中断窗口；
- `DECRBY` 不校验负值同样安全：脚本已先校验 `stock >= quantity` 且原子执行，不会出现负库存。

`SeckillStockRedis.java` 的 Lua 扣减/限购逻辑（`bought + quantity > limit` 判 -1、`stock < quantity` 判 0、limit 默认 1）**判定正确**，无需修改。

---

## 5. 修复顺序与验收对照

| 顺序 | 问题 | 主要验收指标 |
|---|---|---|
| 1 | P1 安全 | 伪造 X-User-Id 被拒绝，userId 来自 token |
| 2 | P2 库存复活 | 活动进行中 Redis 库存不被覆盖 |
| 3 | P3 状态倒退 | ACK 晚到不改写已完成状态 |
| 4 | P4 丢消息+回补 | 消费失败后 Redis 回补、result=failed、MQ 无残留 |
| 5 | P5 重复重发 | 刚发送消息 30s 内不被重投 |
| 6 | P6 回补原子性 | 事务回滚不产生 Redis 误回补 |
| 7 | P9 status=3 死路 | 重复入口可自愈 |
| 8 | P7 契约 | 回补参数与订单一致或已删除 |
| 9 | P8 Redis DB | 黑名单跨服务一致 |
| 10 | P10 | 次要项回归 |

**改完 P1-P6 后**，验收项「无超卖 / 无丢消息 / 无重复订单 / 结果一致」方具备通过基础，再上 JMeter/Gatling 压测验证 500 QPS / P99<800ms，并用进程 kill 场景验证重启不丢消息。

---

## 6. 测试补齐清单

现有 5 个测试（`SeckillConstantsTest`、`SeckillStockRedisTest`、`MallSeckillServiceApplicationTests`）仅覆盖常量与 Bean 装配，**无业务/并发/集成测试**，需新增：

| 类别 | 用例 | 工具 |
|---|---|---|
| Lua 正确性 | 真实 Redis 跑扣减脚本：库存不足 / 限购命中 / limit=1 边界 / 并发扣减 | embedded-redis 或 Testcontainers |
| 并发超卖 | N 线程抢 M<N 库存，断言成功=mq_message 成功数=M、Redis 余额=0 | JUnit 并发 + Testcontainers |
| 幂等重投 | 同一 messageId 重投 3 次，断言订单仅 1 条、状态机不回退 | Testcontainers(MQ) |
| 消费失败补偿 | `deductStock` 返回 0 / DB 异常 → Redis 回补、mq_message=failed、result=failed | Mock + 集成 |
| 状态机时序 | ACK 晚于消费完成 → status 保持 4 | Mock ConfirmCallback |
| warmUp 安全 | 活动进行中触发 warmUp → Redis 库存不回升 | 集成 |
| 超时取消回补 | 建单不支付，延迟消息触发 → order_state=6 + 库存回补 + 幂等 key 生效 | Testcontainers(MQ 延迟插件) |
| 进程 kill 重启 | status=1 消息重启后被 retryExpired 重投成功 | 手动/脚本 |
| 安全 | 持 A 的 token + `X-User-Id: B` 直连 8089 → 拒绝或 userId=A | MockMvc/curl |

---

## 7. 关键文件索引

| 文件 | 作用 |
|---|---|
| `mall-seckill-service/.../controller/SeckillController.java` | P1 |
| `mall-seckill-service/.../service/SeckillApplicationService.java` | P2、P9 |
| `mall-seckill-service/.../service/SeckillOrderService.java` | P1、P10-M6 |
| `mall-seckill-service/.../service/SeckillCompensateService.java` | P6、P7 |
| `mall-seckill-service/.../service/MqMessageService.java` | P5 |
| `mall-seckill-service/.../service/SeckillTask.java` | P2 |
| `mall-seckill-service/.../redis/SeckillStockRedis.java` | P2 |
| `mall-seckill-service/.../mq/SeckillOrderConsumer.java` | P4、P10-M2/M3 |
| `mall-seckill-service/.../config/SeckillMqConfig.java` | P3、P4 |
| `mall-seckill-service/.../controller/InternalSeckillController.java` | P7 |
| `mall-seckill-service/src/main/resources/mapper/MqMessageMapper.xml` | P3、P5 |
| `mall-seckill-service/src/main/resources/application.yml` | P8 |
| `mall-gateway-service/.../config/AuthGlobalFilter.java` | P1 |
| `mall-common-security/.../interceptor/TokenAuthInterceptor.java` | P1（根因参考） |
| `mall-order-service/.../service/OrderApplicationService.java` | P7（对端） |
| `sql/init.sql` | P5（表结构参考） |
