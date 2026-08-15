# 秒杀 Phase 2 开发提示词方案（供 Codex 使用）

> 本文档把 `秒杀方案分阶段实施计划.md` 第四节「Phase 2：可靠性增强」拆成 **7 个可按顺序交付的提示词**。
> 每个提示词自包含：任务背景 → 先读哪些文件 → 具体做什么 → 约束 → 验收标准。
>
> **用法**：先把「一次性上下文」发给 Codex（或保存成项目上下文文件），然后**按 P2-1 → P2-7 顺序**逐个发送提示词，每完成一个**先验收、再进入下一个**。禁止一次性把 7 个全丢给 Codex。

---

## 一、一次性上下文（先发给 Codex）

```text
你正在为 zhiwu-mall 的 mall-seckill-service 开发 Phase 2「可靠性增强」。
开工前先读：CLAUDE.md（必读）、doc/秒杀方案分阶段实施计划.md（第四节 Phase 2 是本次目标）、
doc/基于Redis和MQ实现秒杀订单加购.md（终态参考）。

【关键：代码与 plan 文档的偏差，以代码为准】
1. execute 顺序已调整：plan 3.4.4 写的「先写 mq_message（待扣库存）→ Lua 扣库存」已废弃。
   实际是「先 Redis 原子预扣（库存+限购+在途标记）→ 只有扣减成功的请求才写 mq_message + 发 MQ」。
   见 SeckillApplicationServiceImpl.execute()。
2. mq_message 状态机：不再使用 status=0（待扣库存），createPending 直接落 status=1（待发送）并预留 60s 发送宽限。
3. 新增「在途补偿」：seckill:inflight:{messageId} + seckill:inflight:index（Redis SET），
   SeckillApplicationServiceImpl.recoverOrphanInflightDeducts() 定时回收「已扣库存但未落库」的崩溃遗留。
4. 新增活动缓存：seckill:activity:{activityId}（结束时间戳），requireOpenActivity 走缓存。
5. 消费端幂等现状：SeckillOrderConsumer 用 SETNX seckill:order:{...}（1 PROCESSING/2 SUCCESS/3 FAILED）+ DB 唯一键兜底；
   当前失败走「回补库存 + markFailed + basicAck」——即失败即丢弃，无重试、无 DLQ（Phase 2 要改这里）。
6. 性能配置（别回退）：application.yml 已配 HikariCP maximum-pool-size=50、RabbitMQ listener prefetch=50 + concurrency=5(max 10)。

【通用规范（必须遵守）】
- 响应统一 Result<T> {code,message,data}；错误抛 BizException(ResultCode.X)。
- Redis Key 统一加 mall: 前缀（MallConstants.REDIS_PREFIX），常量集中放 SeckillConstants，勿散落硬编码。
- 秒杀服务是扁平 controller/entity/mapper/service 结构，不做 DDD。
- 只在 mall-seckill-service 内改，不要动 order-service / 其它服务（除非明确要求）。
- 改完必须 mvn -pl mall-seckill-service -am test-compile 编译通过，并补单元测试（src/test 已有 Mockito 用例可参照）。
- RedisLock 已在 mall-common-redis 提供（tryLock / executeIfLocked），分布式锁直接复用，别自己写。
- pom 目前没有 spring-retry / redisson / commons-pool2；如确需新增依赖，先说明理由再改 pom.xml。

【当前 Phase 1 已有能力（Phase 2 在其上增量，别重写）】
- SeckillStockRedis：Redis Lua 原子扣减（含在途标记）+ 回补 + 活动/元数据缓存。
- MqMessageServiceImpl：本地消息表写入/状态流转/Publisher Confirm/retryExpired（生产端发送重投）。
- SeckillOrderServiceImpl.createSeckillOrder：@Transactional 建单（order+order_item+扣 seckill_stock+回写 mq_message=4）。
- SeckillCompensateServiceImpl：cancelAndRestore（超时取消）+ restoreForCancel（用户取消），写 seckill_stock_compensate 流水（DB 唯一键 uk_message_id 幂等）并回补 Redis/DB。
- SeckillTaskImpl：@Scheduled 预热/发送重投/超时扫描/在途回收（当前单实例，无分布式锁）。
- SeckillOrderConsumer / SeckillTimeoutConsumer：MQ 消费。
- SeckillDelayConfig：seckill.delay.exchange(x-delayed-message)/queue 已声明。
- scripts/seckill_loadtest.py：压测脚本（已改成线程池客户端，可复用）。
```

---

## 二、分步提示词

### P2-1：消费失败重试 + DLQ 死信队列

```text
【任务】给秒杀下单消费者加「指数退避重试 + 死信队列(DLQ)」，消灭当前「失败即 ACK 丢弃」的丢消息隐患。

先读：mall-seckill-service 下 SeckillOrderConsumer、SeckillTimeoutConsumer、SeckillMqConfig、
MqMessageServiceImpl、SeckillConstants、application.yml（rabbitmq 段）。

现状问题：SeckillOrderConsumer.handle 在 createSeckillOrder 抛异常时，直接回补库存 + markFailed + basicAck，
消息被丢弃、不进重试。SeckillTimeoutConsumer 也是 basicNack(requeue=false) 直接丢。

要做：
1. 消费端重试（二选一，推荐 A）：
   A. 用 Spring Retry（RetryTemplate 或 @Retryable）在消费者内做指数退避重试：1s → 5s → 30s，最多 3 次；
   B. 或用 TTL+DLX 重试队列拓扑（seckill.order.retry.queue 带 1s/5s/30s TTL，死信到 seckill.dlx）。
2. 重试仍失败 → 投递 DLQ：声明 seckill.order.dlq（durable）+ seckill.dlx（Direct），
   最终失败的消息 basicNack(requeue=false) 进 DLQ，并写一个独立 @RabbitListener 消费 DLQ 做日志告警 + 人工补偿入口。
3. 重试只包「订单创建」这一段：库存回补(restoreStock + markFailed + 置 FAILED 状态)必须是幂等的，
   且只在最终放弃时才执行一次，不要在每次重试时重复回补。
4. 保持与现有 Redis 幂等状态机兼容：重试同一消息时，SETNX 已存在（PROCESSING）要能正确跳过或放行，不能卡死。
5. 在 SeckillConstants 补充新交换机/队列/路由常量；在 SeckillMqConfig 或新 Config 类声明。

约束：不要改 execute 热路径；不要动 mq_message 生产端 retryExpired（那是发送侧重投，与本次消费侧重试不同）。

验收：
- 注入 DB 死锁（消费建单时故意抛异常）→ 日志能看到 1s/5s/30s 三次重试 → 三次后消息进入 DLQ。
- 重试期间库存只回补一次（幂等），不重复回补。
- 正常消息仍走「消费成功 → 回写 mq_message=4 → ACK」，不退化。
- 补单元测试：重试次数用尽进 DLQ、重试不重复回补库存。
- mvn -pl mall-seckill-service -am test-compile 通过。
```

### P2-2：库存补偿流水表 + 补偿服务闭环

```text
【任务】新增 seckill_stock_compensate 流水表，把所有库存回补都记录流水并保证幂等，形成补偿闭环。

先读：SeckillCompensateServiceImpl、SeckillConstants、sql/_apply_seckill.sql、sql/init.sql（order/seckill_item 结构）、
SeckillOrderConsumer（下单失败回补的调用点）。

要做：
1. 建表（新建 sql/_apply_seckill_phase2.sql，勿改 init.sql）：
   按 plan 4.4.3 的 DDL 建 seckill_stock_compensate（含 uk_message_id(message_id) 唯一键、
   compensate_type 1下单失败/2支付超时/3用户取消/4对账偏差、status 0待处理/1已完成/2失败）。
2. 新增 SeckillStockCompensateDO（entity，extends BaseEntity）+ SeckillStockCompensateMapper（+ XML）。
3. 重构 SeckillCompensateServiceImpl：把现有 cancelAndRestore / restoreForCancel / restoreStockOnce 的回补动作，
   统一走一个「写补偿流水（INSERT ... 用 uk_message_id 去重，冲突即视为已补偿，直接返回）→ 回补 Redis + DB」的公共方法。
   覆盖类型：下单失败(1)、支付超时(2)、用户取消(3)。对账偏差(4)留到 P2-3 调用。
4. 幂等原则：以流水表唯一键（uk_message_id，单列）为「是否已补偿」的唯一事实源；不再使用 Redis restoreKey 占位（其 SETNX 在回补前占位会在失败/崩溃时卡死重试）。
5. 消费者 SeckillOrderConsumer 下单失败时回补，也要走这个流水（compensate_type=1）。

约束：不改库存扣减主链路；回补必须 Redis + DB 双写保持一致（现有 restoreStockOnce 的 afterCommit 模式保留）。

验收：
- 同一订单重复触发回补（超时取消 + 用户取消双触发）→ 流水表只有 1 条、库存只回补一次。
- 4 类补偿都落流水（对账偏差类型可先用单测 mock 验证）。
- 补单元测试：重复回补幂等、四种 compensate_type 都写对流水。
- mvn test 编译通过。
```

### P2-3：全链路对账任务

```text
【任务】新增对账服务，分钟级 + 小时级两条对账链路，偏差自动记补偿流水 + 告警日志。

先读：MqMessageServiceImpl（mq_message 状态语义）、OrderMapper/OrderItemMapper、SeckillStockRedis、SeckillItemMapper、
SeckillConstants、P2-2 新增的 SeckillStockCompensateService。

要做：
1. 新增 SeckillReconcileService（service + impl）：
   - 分钟级（@Scheduled 60s）：
     a. mq_message 待发送(status=1 且 next_retry_time 已过)条数 vs RabbitMQ seckill.order.queue 积压数，偏差 → 补发/告警。
     b. mq_message 已发送/已完成(status=2/4) 但 order 表无对应 order_source=2 记录 → 告警 + 补单标记。
   - 小时级（@Scheduled 每小时）：
     Redis seckill:stock:{activityId}:{seckillItemId} vs seckill_item.seckill_stock，不一致 → 以 MySQL 为准校准 Redis（回写），并告警。
2. 对账发现的「库存偏差」用 P2-2 的补偿服务写流水（compensate_type=4 对账偏差）。
3. 队列积压数获取：优先用 RabbitMQ HTTP 管理 API（http://localhost:15672/api/queues/%2Fmall/seckill.order.queue，用 admin/123456）；
   拿不到 API 就降级为「仅对 mq_message 与 order 表对账」，并在日志注明跳过 MQ 积压项。
4. 结果输出结构化日志（[seckill-reconcile] 前缀），便于后续接监控。

约束：对账任务只读 + 少量校准写，不能阻塞主链路；单实例跑（分布式锁在 P2-4 统一加）。

验收：
- 手动把某条 mq_message 置成 status=1 但消息实际丢失 → 分钟级对账能发现并告警。
- 手动把 Redis 库存改成和 MySQL 不一致 → 小时级对账能把 Redis 校准回 MySQL 值并记 compensate_type=4 流水。
- 补单元测试：对账偏差检测逻辑（mock mapper 验证调用）。
- mvn test 编译通过。
```

### P2-4：定时任务分布式锁 + 幂等

```text
【任务】给 SeckillTaskImpl 的所有 @Scheduled 任务加 Redis 分布式锁，保证多实例部署时同一任务只执行一次。

先读：SeckillTaskImpl、mall-common-redis 的 RedisLock（tryLock / executeIfLocked，已可用，别自己写）。

要做：
1. 给这 4 个任务加锁（用 redisLock.executeIfLocked，锁 key 见下，TTL 略大于任务最长执行时长）：
   - warmUpActiveItems（预热/刷新元数据）→ key: seckill:task:refresh-meta
   - retryPendingMessages（发送重投）→ key: seckill:task:retry-pending
   - cancelExpiredOrders（超时扫描）→ key: seckill:task:cancel-expired
   - recoverOrphanInflightDeducts（在途回收）→ key: seckill:task:recover-inflight
   （P2-3 的对账任务也加锁：seckill:task:reconcile-minute / seckill:task:reconcile-hour）
2. 锁 key 常量加到 SeckillConstants。
3. 加锁失败（未抢到）→ 直接 return，日志 debug，不抛异常。
4. 注入 RedisLock 依赖到 SeckillTaskImpl（它已在 mall-common-redis 自动装配，直接 @RequiredArgsConstructor 注入）。

约束：锁只是「防多实例重复执行」，不改变单实例下的既有行为；别把 @Scheduled 改成手动线程池。

验收：
- 补单元测试：加锁失败时任务体不执行（mock RedisLock.executeIfLocked 返回 false，验证业务方法未调用）。
- mvn test 编译通过。
```

### P2-5：批量刷库 + 批量 ACK

```text
【任务】把秒杀下单消费者从「单条建单」升级为「批量刷库 + 批量 ACK」，提升建单吞吐。

先读：SeckillOrderConsumer、SeckillOrderServiceImpl（createSeckillOrder）、OrderMapper/OrderItemMapper（+ XML）、
SeckillItemMapper（deductStock）、application.yml（listener 段）。

现状：消费者 prefetch=50、concurrency=5，但每条消息单独调 createSeckillOrder（一条一个 @Transactional，约 6~7 次 DB 往返）。

要做：
1. 双阈值批量刷库：内存 BatchQueue 缓冲消息，size>=1000 或距上次刷库>=1s 触发一次批量落库
   （Phase 2 本机压测可用更小的阈值如 size>=100 / 500ms 便于验证）。
2. 批量建单：批量 INSERT INTO `order` ... ON DUPLICATE KEY UPDATE（靠 uk_user_activity_item 幂等）、
   批量 INSERT order_item、批量 UPDATE seckill_item.seckill_stock（累减）、批量回写 mq_message.status=4。
3. 部分失败处理：唯一键冲突 → 视为成功；其它异常 → 该条单独 NACK，不回滚整批。
4. 批量 ACK：整批事务提交成功后，对这批 deliveryTag 统一 basicAck；失败条目 basicNack。
5. 与 P2-1 的重试/DLQ 兼容：最终失败条目仍走 P2-1 定义的重试与 DLQ 路径。
6. 保留现有单条 createSeckillOrder 作为批量失败时的降级单条处理（或明确说明为何不需要）。

约束：这是 Phase 2 最复杂的一步，先想清楚再写；不得破坏「无超卖/无重复订单」的既有验收；
      库存扣减必须带 WHERE seckill_stock>=quantity 条件（DB 最后防线）。

验收：
- 单消费者批量模式吞吐显著高于单条（目标参考 plan 4.5：单消费者 3000+ msg/s 落库）。
- 1000 条里混入 100 条重复 messageId → 订单表仍只有唯一记录，重复条目不报错。
- 补单元测试：批量 flush 触发阈值、唯一键冲突视为成功、部分失败单条 NACK。
- 跑 scripts/seckill_loadtest.py run --reset --requests 500 --stock 100 --users 500 对账全 PASS。
```

### P2-6：消费者动态扩容

```text
【任务】让秒杀消费者能根据消息积压量动态调整并发，避免积压时消费跟不上。

先读：SeckillOrderConsumer、application.yml（listener.simple 段）、SeckillConstants、SeckillTaskImpl。

要做：
1. 提供「积压量」指标：@Scheduled(30s) 定时采集 RabbitMQ 队列积压数（HTTP 管理 API，同 P2-3 的取法）
   和 mq_message 待处理数，输出结构化日志（[seckill-backlog] 前缀）+ 可选 metrics。
2. 动态扩容（选一种，推荐 A 的简化版）：
   A. 配置化 + 手动/脚本触发：把 concurrency 抽成配置，暴露一个 /internal/seckill/consumer/scale 接口（仅内网）或
      一个管理命令，按积压阈值调整 SimpleMessageListenerContainer 的 concurrentConsumers。
   B. 全自动：定时判断积压 > 阈值 → 上调 concurrency（不超过 max-concurrency）；积压回落 → 下调。
3. 无论哪种，都要在日志里记录「当前 concurrency / 队列积压数 / 调整动作」，便于验证。

约束：调整并发不能中断正在消费的消息；上限不超过 application.yml 的 max-concurrency；不做跨实例协调（单实例内即可）。

验收：
- 人工把队列塞入大量消息 → 观察到 concurrency 上调、积压被更快消化。
- 积压回落后 concurrency 回落。
- mvn test 编译通过。
```

### P2-7：混沌测试 + Phase 2 验收

```text
【任务】编写 Phase 2 的异常注入/混沌测试脚本，并完成 Phase 2 全部验收。

先读：scripts/seckill_loadtest.py、doc/秒杀方案分阶段实施计划.md 4.5 验收标准、P2-1~P2-6 产出的代码。

要做（新增 scripts/seckill_chaos_test.py，复用 loadtest 的造数/对账能力）：
1. 场景一「DB 死锁/异常」：通过临时关停 MySQL 或注入慢 SQL，制造消费建单失败，验证重试 + DLQ + 恢复后消息最终落库。
2. 场景二「网络断开/MQ 重启」：停 RabbitMQ 一段时间再恢复，验证消息不丢（mq_message 待发送被 retryExpired 补发）。
3. 场景三「消费者宕机」：压测中途 kill seckill-service 再重启，验证在途消息/订单状态可恢复、无超卖无重复。
4. 每个场景结束调用对账（复用 loadtest 的 reconcile），输出 PASS/FAIL。

对照 plan 4.5 输出验收报告：
- 异常注入不丢消息（DB宕机/网络断开/消费者kill 后恢复，消息最终全部落库）
- DLQ 生效（重试 3 次仍失败进 DLQ 并告警）
- 补偿闭环（支付超时取消后 Redis 库存正确回补）
- 对账发现偏差（人工改数据 → 对账能发现并告警）
- 定时补偿幂等（同一条待发送被多次扫描只投递一次）

约束：混沌脚本只在本地/测试环境跑，不要误伤正式数据；每步有明确断言，失败要打印差异。

验收：跑完 4 个场景全部 PASS，产出 Phase 2 验收报告。
```

---

## 三、验收清单映射（Phase 2 Checklist ↔ 提示词）

| 完成 | Plan 4.x 验收项 | 对应提示词 |
|---|---|---|
| [x] | DLQ 队列与死信消费者 | P2-1 |
| [x] | 重试机制（指数退避） | P2-1 |
| [ ] | 批量刷库（双阈值 + ON DUPLICATE） | P2-5 |
| [x] | 库存补偿流水表 + 补偿服务 | P2-2 |
| [x] | 全链路对账（分钟级 + 小时级） | P2-3 |
| [x] | 定时补偿任务（分布式锁 + 幂等） | P2-4 |
| [x] | 消费者动态扩容 | P2-6 |
| [ ] | 混沌测试 + 验收 | P2-7 |

## 四、执行顺序与依赖

```text
P2-1（重试+DLQ）─┐
                 ├─→ P2-5（批量刷库，需兼容 P2-1 的重试/DLQ）
P2-2（补偿流水）─┼─→ P2-3（对账，依赖 P2-2 的流水表）
P2-4（分布式锁）─┘
P2-6（动态扩容，独立）
P2-7（混沌验收，依赖全部）
```

- 建议顺序：P2-1 → P2-2 → P2-3 → P2-4 → P2-5 → P2-6 → P2-7。
- P2-2 和 P2-4 相对独立，可与 P2-1 并行（若用多个 Codex 实例），但 P2-5 必须等 P2-1 完成。

---

## 五、每个提示词的额外背景片段（随提示词一起粘贴）

> 用法：发送 P2-N 提示词时，把下面 P2-N 的「背景片段」**一起粘贴**。目的是让 Codex 在看到
> 当前真实代码前就知道「改动点长什么样、坑在哪」，避免它按 plan 文档的旧描述误改。
> 代码已在库里，片段只是「精读 + 标注」，Codex 仍要回到原文件看完整上下文。

### P2-1 背景片段（消费端现状：失败即丢弃）

```text
当前 SeckillOrderConsumer.handle 的幂等 + 异常处理长这样（P2-1 要改的就是 catch 段）：

    Boolean locked = redis.setIfAbsent(orderKey, "1"/*PROCESSING*/, 30min);
    if (!locked) {
        state = redis.get(orderKey);
        if (state == "2"/*SUCCESS*/) { basicAck; return; }
        if (state == "3"/*FAILED*/)   { redis.delete(orderKey); basicNack(requeue=true); return; }
        basicNack(requeue=true); return;   // PROCESSING 状态：重入队
    }
    seckillOrderService.createSeckillOrder(dto);
    redis.set(orderKey, "2"); basicAck;
    // catch DuplicateKeyException → set "2" + basicAck（视为成功）
    // catch Exception → restoreStock + markFailed + set "3" + basicAck  ← ★失败即 ACK 丢弃，无重试

SeckillTimeoutConsumer.handle：成功 cancelAndRestore+ack；异常 basicNack(requeue=false)（也是直接丢）。

注意点：
1. 「PROCESSING 状态 → nack(requeue=true)」当前若消息卡在 PROCESSING 会热循环 requeue，P2-1 加重试时要一并处理这个状态
   （例如给 PROCESSING 状态也加 TTL 兜底，或重试时跳过仍 PROCESSING 的消息）。
2. 重试只包 createSeckillOrder，restoreStock/markFailed/set FAILED 只在最终放弃时执行一次。
3. mq_message 的 retryExpired（生产端发送重投）是另一条链路，别动它。
```

### P2-2 背景片段（补偿服务现状：Redis SETNX 幂等，无流水表）

```text
SeckillCompensateServiceImpl 当前：
- cancelAndRestore(orderId)：超时取消，校验 order_source=2 && order_state=1 → cancelPendingOrder → restoreStockOnce。
- restoreForCancel(orderNo[, dto])：用户取消，校验归属 → cancelPendingOrder → restoreStockOnce。
- restoreStockOnce(order)：Phase 1 用 redis.setIfAbsent(restoreKey(orderNo), "1", 7天) 做幂等；P2-2 起改为写 seckill_stock_compensate 流水（DB 唯一键）做幂等，restoreKey 已移除。
  然后 orderItemMapper.selectFirstByOrderId → seckillItemMapper.restoreStock(DB) →
  afterCommit 里 seckillStockRedis.restoreStock(Redis)。

P2-2 要做：把「Redis SETNX 幂等」升级为「写 seckill_stock_compensate 流水（uk_message_id 唯一键）→ 回补 Redis+DB」。
restoreKey 的 Redis SETNX 已移除，DB 唯一键（uk_message_id）是唯一幂等依据。

注意点：
1. 下单失败（消费者 catch 里的 restoreStock）也要走流水，compensate_type=1。
2. restoreStock 是 Redis+DB 双写，afterCommit 模式要保留（DB 回滚则 Redis 不回补）。
3. messageId 是 userId:activityId:seckillItemId；但超时取消/用户取消只有 orderNo，流水表 uk 用 (message_id, compensate_type)，
   需约定：取消类流水 message_id 用 orderNo 还是 messageId（建议统一用 orderNo 作为取消类流水的 message_id 字段值，避免空）。
```

### P2-3 背景片段（对账的数据源与状态语义）

```text
mq_message 状态机（SeckillConstants）：0待扣库存(已不用) / 1待发送 / 2已发送 / 3发送失败 / 4已完成。
- execute 只对胜者写 mq_message，直接落 status=1（待发送），60s 发送宽限（next_retry_time）。
- confirm 回调 markSent → status=2；消费者建单成功 markDone → status=4；发送失败 markFailed → status=3。
- retryExpired 扫描 status=1 且 next_retry_time 已过的记录重投（生产端补发）。

order 表秒杀订单：order_source=2 + activity_id + seckill_item_id，唯一键 uk_user_activity_item(user_id,activity_id,seckill_item_id)。

Redis 库存：mall:seckill:stock:{activityId}:{seckillItemId}（预热写入，Lua 扣减）。
DB 库存：seckill_item.seckill_stock（消费者建单事务内扣减）。

对账口径（plan 4.4.4）：
- 分钟级：mq_message 待发送数 vs 队列积压数；mq_message(status=2/4) 无对应 order → 告警。
- 小时级：Redis seckill:stock:* vs seckill_item.seckill_stock，不一致以 MySQL 为准校准 Redis。

注意点：
1. 队列积压数要访问 RabbitMQ 管理 API（/api/queues/%2Fmall/seckill.order.queue，admin/123456），拿不到就降级跳过该子项。
2. 对账偏差写 P2-2 的流水，compensate_type=4。
```

### P2-4 背景片段（定时任务现状 + RedisLock API）

```text
SeckillTaskImpl 现有 4 个 @Scheduled（当前无锁）：
- warmUpActiveItems()          60s   → 刷新活动/元数据缓存
- retryPendingMessages()       30s   → mqMessageService.retryExpired(200)
- cancelExpiredOrders()        60s   → 扫过期待支付秒杀订单，逐个 cancelAndRestore
- recoverOrphanInflightDeducts() 30s → 回收「已扣库存未落库」的崩溃遗留
（P2-3 新增的对账任务也要加锁）

RedisLock（mall-common-redis 已提供，直接注入，别自己写）：
- String tryLock(String key, long ttlSeconds) → 成功返回 lockId，失败 null
- boolean executeIfLocked(String key, long ttlSeconds, Runnable task) → 抢到锁才执行，内部自动释放

注意点：
1. 锁 key 建议 seckill:task:<name>（RedisLock 内部会自动加 lock: 前缀）。
2. TTL 略大于任务最长执行时长（如 retryPendingMessages 给 60s）。
3. 加锁失败静默 return（debug 日志），不要抛异常。
```

### P2-5 背景片段（批量刷库要替换的单条路径）

```text
当前单条路径 SeckillOrderServiceImpl.createSeckillOrder（@Transactional，一条消息一次事务，约 6~7 次 DB 往返）：
  ① userAddressMapper.selectByIdAndUserId
  ② productMapper.selectById(spuId) + productSkuMapper.selectById(skuId)
  ③ orderMapper.insert(order)   // order_source=2 + activity_id + seckill_item_id
  ④ orderItemMapper.insert(item)
  ⑤ seckillItemMapper.deductStock(seckillItemId, quantity)  // UPDATE ... WHERE seckill_stock>=quantity；影响行数=0 抛异常回滚
  ⑥ mqMessageService.markDone(messageId)
  ⑦ registerTimeoutMessage(orderId)  // 事务提交后发延迟消息

消费端 SeckillOrderConsumer 是 prefetch=50 + concurrency=5，但每条消息单独调 createSeckillOrder。

P2-5 批量化要点：
- 双阈值（size>=1000 或 >=1s）flush；本机验证可先用 size>=100 / 500ms。
- 批量 INSERT order ... ON DUPLICATE KEY UPDATE（靠 uk_user_activity_item 幂等）；批量 order_item；批量 deductStock；批量 markDone。
- 唯一键冲突视为成功；其它异常单条 NACK；整批事务提交后统一 ACK。

注意点：
1. 批量路径里 deductStock 必须仍带 WHERE seckill_stock>=quantity 条件，否则破坏「DB 最后防线」。
2. 别删单条 createSeckillOrder，保留作批量失败时的降级（或明确论证不需要）。
3. 与 P2-1 的重试/DLQ 兼容：批量中最终失败的条目走 P2-1 定义的重试。
```

### P2-6 背景片段（监听器配置现状）

```text
application.yml 当前 listener 段：
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 50
        concurrency: 5
        max-concurrency: 10

消费者 SeckillOrderConsumer 用 @RabbitListener(queues = SeckillConstants.SECKILL_QUEUE)，
默认绑定 SimpleMessageListenerContainer。

P2-6 动态扩容：动态改 SimpleMessageListenerContainer 的 concurrentConsumers（Spring AMQP 运行时可变），
上限不超过 max-concurrency；积压指标用 RabbitMQ 管理 API + mq_message 待处理数。

注意点：调整 concurrency 不能中断在途消息（Spring AMQP 支持平滑调整）；单实例内即可，不做跨实例协调。
```

### P2-7 背景片段（压测脚本可复用能力）

```text
scripts/seckill_loadtest.py 现有能力（P2-7 混沌脚本直接复用）：
- mint_jwt(secret, user_id, issuer, ttl)：离线签 JWT
- seed_data(cfg)：造 user + user_address（幂等）
- reset_data(cfg)：清秒杀订单/mq_message + 重置 Redis 库存到 --stock
- fetch_address_map(cfg)：{user_id: address_id}
- 线程池压测引擎（run_oneshot / run_sustained）
- reconcile(cfg)：对账（无超卖 / DB扣减==订单 / 订单==mq完成 / Redis扣减==订单 / 无重复订单）

混沌场景在它基础上加：
- 场景一 DB 异常：临时停 MySQL 或注入慢 SQL → 验证重试+DLQ+恢复后落库
- 场景二 MQ 重启：停 RabbitMQ 再恢复 → 验证 mq_message 待发送被 retryExpired 补发
- 场景三 消费者宕机：压测中 kill seckill-service 再重启 → 验证无超卖无重复

注意点：每场景结束跑 reconcile；断言失败要打印差异（Redis/DB/订单三处数字）。
```
