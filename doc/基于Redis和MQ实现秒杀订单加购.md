# 秒杀系统 Redis + RabbitMQ 技术方案（生产优化版）

> **修订记录（2026-08-12）**：与 `sql/init.sql` / `sql/_apply_seckill.sql` 已落库 schema 校对后修订——① messageId 统一 SKU 维度 `seckillItemId`（替代 `productId`）；② `mq_message.product_id` → `seckill_item_id`（+ `spu_id`/`sku_id`/`quantity` 冗余）；③ Lua 脚本支持 `limit_per_user` 与 `quantity`；④ 秒杀入口增加活动时间窗口校验；⑤ 消息体携带下单完整快照（秒杀价/地址）；⑥ 消费端重试上限防毒消息热循环；⑦ 明确「先写消息表」顺序的 DB 写入代价与取舍。实施范围与验收以 [秒杀方案分阶段实施计划.md](./秒杀方案分阶段实施计划.md) 为准。

```markdown
## 一、系统目标
针对高并发秒杀场景，设计一套基于 Redis Cluster + RabbitMQ Cluster + MySQL 的生产级秒杀系统。
主要解决以下问题：
- 高并发秒杀请求
- 热点商品库存竞争
- 库存超卖与少卖
- Redis 主从切换库存回退
- MQ 消息丢失
- MQ 消息重复消费
- MQ 消息积压
- 批量消费部分失败
- 数据库高并发写入
- Redis 热点节点负载不均
- 订单最终一致性
- 秒杀防刷与风控
- 多级故障降级

核心技术：
```text
Redis Cluster
RabbitMQ Cluster
MySQL
Lua 原子脚本
本地消息表
Redis 状态机幂等
MySQL UNIQUE KEY
MQ 重试 + 死信队列
批量刷库 + 部分失败处理
库存补偿闭环
全链路对账
延迟队列（支付超时）
```
---
# 二、整体架构
```text
                         用户
                          |
                          v
                    +-----------+
                    | 网关/限流  |
                    | 风控/防刷  |
                    +-----------+
                          |
                          v
                    +-----------+
                    | 秒杀服务  |
                    +-----------+
                          |
                          v
                  +----------------+
                  |  Redis Cluster |
                  |                |
                  | Master1 Slave1 |
                  | Master2 Slave2 |
                  | Master3 Slave3 |
                  +----------------+
                          |
                    Lua 原子扣库存
                          |
                          v
                  +----------------+
                  |  本地消息表     |
                  |  (MySQL)       |
                  +----------------+
                          |
                          v
                  +----------------+
                  | RabbitMQ 集群  |
                  |                |
                  | Quorum Queue   |
                  | 死信队列        |
                  | 延迟队列        |
                  +----------------+
                          |
                          v
                  +----------------+
                  | 订单消费者集群  |
                  +----------------+
                          |
                          v
                  +----------------+
                  |     MySQL      |
                  |                |
                  | 订单表          |
                  | 本地消息表      |
                  | 商品库存表      |
                  +----------------+
```
核心思想：
> Redis 负责高并发流量和库存竞争，RabbitMQ 负责削峰填谷与异步解耦，MySQL 负责最终业务数据持久化和一致性兜底，全链路具备异常自愈与对账能力。

---
# 三、核心秒杀流程
```text
用户
 |
 v
网关限流/风控
 |
 v
秒杀服务（校验活动时间窗口：未开始/已结束直接拒绝）
 |
 v
写入本地消息表（状态：待扣库存）
 |
 v
Redis Lua 原子扣库存
 |
 +---- 库存不足 ------> 更新消息表为失败 --> 秒杀失败
 |
 +---- 用户已购买 ----> 更新消息表为失败 --> 秒杀失败
 |
 +---- 成功
        |
        v
   更新本地消息表为「待发送」
        |
        v
   生成业务 messageId
        |
        v
   发送 RabbitMQ
        |
        v
   收到 Confirm ACK --> 更新消息表为「已发送」
        |
        v
   订单消费者接收消息
        |
        v
   Redis 状态机幂等校验
        |
        v
   进入批量缓冲区
        |
        v
   双阈值触发批量刷库
        |
        v
   MySQL 事务提交成功
        |
        v
   更新 Redis 幂等状态为成功
        |
        v
   批量 ACK 消息
```

---
# 四、业务 messageId 设计
本系统不使用 RabbitMQ 自动生成的 Message ID 作为业务幂等依据。
采用业务唯一标识（**SKU 维度**，核心为 `seckill_item.id`）：
```text
messageId = 用户ID + 活动ID + 秒杀商品项ID
```
例如：
```text
userId = 8888
activityId = 1001
seckillItemId = 2001
```
生成：
```text
messageId = 8888:1001:2001
```
最终建议统一格式：
```text
{userId}:{activityId}:{seckillItemId}
```
> ⚠️ **维度说明（重要）**：原方案用 `productId`（SPU 维度），但 `order.uk_user_activity_item` 唯一索引是按 `seckill_item_id`（SKU 维度）建的，二者不一致会直接导致幂等失效。**本方案统一为 SKU 维度**，`messageId`、`mq_message.message_id`、Redis 幂等 Key、`order` 唯一索引、Redis 库存 Key、限购 Key 全部以 `seckillItemId`（= `seckill_item.id`）为核心。

---
# 五、为什么使用业务 messageId
秒杀场景中：
```text
用户ID + 活动ID + 秒杀商品项ID（SKU 维度）
```
本身就代表：
> 某个用户在某个活动中购买某个秒杀商品项的一次秒杀订单。

因此：
```text
userId + activityId + seckillItemId
```
天然具有业务唯一性（同一用户、同一活动、同一 SKU 只能下一单，由 `order.uk_user_activity_item` 保证）。
该 ID 可同时用于：
```text
MQ 业务消息ID
本地消息表唯一标识（mq_message.message_id）
Redis 幂等 Key
订单业务唯一标识（order.uk_user_activity_item）
库存补偿流水标识
```

---
# 六、messageId 示例
MQ 消息体（**携带下单完整快照，消费端不查库**，避免秒杀价/商品信息中途被改导致漂移）：
```json
{
    "messageId": "8888:1001:2001",
    "userId": 8888,
    "activityId": 1001,
    "seckillItemId": 2001,
    "spuId": 4001,
    "skuId": 3001,
    "seckillPrice": 99.00,
    "price": 129.00,
    "quantity": 1,
    "addressId": 5001,
    "createTime": "2026-01-01 12:00:00"
}
```
> `seckillItemId` = `seckill_item.id`；`seckillPrice` 为秒杀价快照（写入 `order_item.cur_price`），`price` 为 SKU 原价（写入 `order_item.price`）。收货地址由用户在秒杀时选定，随请求一并提交（秒杀订单是支付前异步创建的，必须先有地址快照）。

---
# 七、Redis 库存预热
秒杀活动开始之前，将秒杀库存加载到 Redis（数据源为 `seckill_item.seckill_stock`，按 `seckill_item.id` 维度）。
Redis Key（正式格式，无 Hash Tag；Phase 3 集群再加 Hash Tag，见第十节）：
```text
seckill:stock:{activityId}:{seckillItemId}
```
例如：
```text
seckill:stock:1001:2001 = 1000
```
秒杀过程中不直接访问 MySQL 查询库存。
预热完成后需执行一次库存对账，确保 Redis 与 MySQL 初始库存一致（`Σ Redis 库存 = Σ seckill_item.seckill_stock`）。
> ⚠️ **预热与活动窗口**：预热应只对「已启用且当前在活动时间窗口内」的活动执行；秒杀入口还要做活动时间窗口校验（未开始/已结束直接拒绝，见第三节），避免库存被预热后、活动开始前就被抢空。

---
# 八、Redis Cluster
生产环境使用 Redis Cluster。
```text
                    Redis Cluster
        +---------------+---------------+
        |               |               |
     Master1         Master2         Master3
        |               |               |
     Slave1          Slave2          Slave3
```
Redis Cluster 负责：
* 商品热点缓存
* 秒杀库存扣减
* 用户限购校验
* 请求限流计数
* 消费幂等控制
* 热点数据承载

---
# 九、Redis Cluster + Lua
秒杀过程需要同时完成：
```text
判断用户是否已经购买
+
判断库存数量
+
扣减库存
+
记录用户购买状态
```
必须保证这些操作的原子性，因此使用 Lua 脚本。

---
# 十、Redis Hash Tag 与热点优化
Redis Cluster 中 Lua 脚本涉及的多个 Key 必须位于同一个 Hash Slot，因此使用 Hash Tag。**Phase 1/2 单机不需要 Hash Tag**，库存/限购 Key 用第七节/第四十四节的正式格式；Phase 3 上集群时为满足 Lua 多 Key 同槽，给 Key 加 `{activityId}` Hash Tag：
```text
seckill:{1001}:stock:2001
seckill:{1001}:user:2001:8888
```
其中 `{1001}` 保证两个 Key 使用相同的 Hash Slot（`2001` 为 `seckillItemId`）。

**热点商品优化：**
单商品大促场景下，同一 Hash Tag 会导致流量集中到单个节点，需做库存分片：
- 将总库存拆分为 N 份，分散到不同分片 Key 中
- 分片 Key 使用不同 Hash Tag，分散到不同 Redis 节点
- 秒杀请求轮询或随机访问分片，扣减失败则换下一分片
- 库存预热时均匀分配到各分片

示例（Phase 3）：
```text
seckill:{1001_01}:stock:2001
seckill:{1001_02}:stock:2001
seckill:{1001_03}:stock:2001
```

---
# 十一、Lua 原子扣库存
```lua
-- KEYS[1] = seckill:stock:{activityId}:{seckillItemId}
-- KEYS[2] = seckill:user:{activityId}:{seckillItemId}:{userId}
-- ARGV[1] = 限购Key TTL（秒，建议=活动剩余时长，防止取消后无法重抢）
-- ARGV[2] = 本次购买数量 quantity（≥1）
-- ARGV[3] = limit_per_user（seckill_item.limit_per_user）
local stockKey = KEYS[1]
local userKey = KEYS[2]
local expireTime = tonumber(ARGV[1])
local quantity = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

-- 校验本次购买数量合法性
if quantity < 1 then
    return -2
end

-- 校验用户累计已购量是否已达上限（限购 Key 存累计已购数量，非布尔值）
local bought = tonumber(redis.call('GET', userKey))
if bought and bought + quantity > limit then
    return -1
end

-- 获取库存
local stock = tonumber(redis.call('GET', stockKey))
if not stock or stock < quantity then
    return 0
end

-- 扣减库存（DECRBY）
redis.call('DECRBY', stockKey, quantity)
-- 累计用户购买量（INCRBY）+ 重置 TTL
redis.call('INCRBY', userKey, quantity)
redis.call('EXPIRE', userKey, expireTime)

return 1
```
返回值：
```text
1   秒杀成功
0   库存不足
-1  达到限购上限（累计已购 quantity 已达 limit_per_user）
-2  非法购买数量（quantity < 1）
```

> ⚠️ **限购语义**：`seckill_item.limit_per_user` 默认 1。限购 Key 记录的是**累计已购数量**而非布尔值，因此脚本天然支持 `limit_per_user > 1` 与 `quantity > 1`。若业务不开放多件限购，保持 `limit_per_user=1`、入口将 `quantity` 恒置 1 即可。

**主从一致性增强：**
Lua 执行成功后，可选择性执行 `WAIT 1 100` 命令，强制等待至少 1 个从节点同步完成，牺牲少量性能降低主从切换库存回退概率（Phase 3 集群场景）。

---
# 十二、秒杀成功生成 messageId
Redis Lua 执行成功后：
```text
用户ID + 活动ID + 秒杀商品项ID
```
生成全局唯一业务 messageId，后续整个订单链路统一使用该 ID。
```text
messageId
    |
    +-- 本地消息表唯一键
    |
    +-- RabbitMQ 业务消息ID
    |
    +-- Redis 幂等 Key
    |
    +-- MySQL 订单唯一标识
    |
    +-- 库存补偿流水标识
```

---
# 十三、MQ 下单消息保证不丢失（全链路闭环方案）
## 问题
如果 Redis 扣库存成功后，MQ 发送失败且无补偿，会出现：
```text
Redis 库存减少
但是没有对应订单
```
因此需要保证：Redis 扣库存成功之后，MQ 消息最终能够成功进入 RabbitMQ，并可靠地被消费落库。

## 设计原则
在高并发场景下，保证 MQ 下单消息**绝对不丢失**，必须构建"发送端可靠投递 + Broker 多副本持久化 + 消费端事务提交后 ACK + 后台定时对账"的**全链路闭环机制**，单一环节都无法独立保证：
```text
生产者可靠投递
+
Broker 多副本持久化
+
消费端事务后 ACK
+
定时对账补发
+
下游幂等配合
=
消息绝对不丢失
```

---
## 1. 生产者端（避免发送过程丢失）

### 1.1 本地消息表（双写兜底）
在 Redis 扣减成功后，将下单消息先写入 MySQL 本地消息表（初始状态为 `待发送`）。MQ 发送成功且收到 Confirm 回执后再修改为 `已发送`。

本地消息表 DDL（项目数据库 `mall` 中**新建**，与 `sql/init.sql` 风格保持一致）：
```sql
CREATE TABLE `mq_message` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `message_id`      VARCHAR(128)    NOT NULL                COMMENT '业务唯一ID（userId:activityId:seckillItemId）',
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `activity_id`     BIGINT UNSIGNED NOT NULL,
  `seckill_item_id` BIGINT UNSIGNED NOT NULL               COMMENT '秒杀商品项ID，关联 seckill_item.id（SKU 维度）',
  `spu_id`          BIGINT UNSIGNED DEFAULT NULL           COMMENT '冗余 SPU 维度，便于对账',
  `sku_id`          BIGINT UNSIGNED DEFAULT NULL           COMMENT '冗余 SKU 维度，便于对账',
  `quantity`        INT             NOT NULL DEFAULT 1     COMMENT '购买数量',
  `status`          TINYINT         NOT NULL DEFAULT 0      COMMENT '0-待扣库存 1-待发送 2-已发送 3-发送失败 4-已完成',
  `retry_count`     INT             NOT NULL DEFAULT 0      COMMENT '重试次数',
  `next_retry_time` DATETIME        DEFAULT NULL            COMMENT '下次重试时间',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_status_next_time` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 本地消息表（秒杀下单可靠投递凭证）';
```
> ⚠️ **与已落库 schema 的差异**：`sql/init.sql` 与 `sql/_apply_seckill.sql` 中该表当前为 `product_id`（SPU 维度，注释 `userId:activityId:productId`），与 SKU 维度决策不一致。**Phase 1 需先迁移**：`ALTER TABLE mq_message CHANGE product_id seckill_item_id BIGINT UNSIGNED NOT NULL`，并补 `spu_id`/`sku_id`/`quantity` 冗余列。

### 1.2 Publisher Confirm 机制
开启 RabbitMQ 的异步 Confirm / Returns 确认回调：
- 收到 Broker ACK → 更新本地消息表为 `已发送`
- 收到 NACK 或超时未回应 → 保留 `待发送` 状态，由补偿线程重试

```text
Producer
   |
   | messageId = 8888:1001:2001
   v
RabbitMQ
   |
   +---- ACK  ----> 更新消息表为「已发送」
   |
   +---- NACK ----> 保持「待发送」
   |
   +---- 超时 ----> 保持「待发送」
                        |
                        v
                    定时补偿重发
```

### 1.3 生产者端执行流程
```text
用户请求到达
        |
        v
写入本地消息表（状态=待扣库存）
        |
        v
执行 Redis Lua 扣库存
        |
        +---- 失败 ----> 更新消息状态为失败 --> 返回秒杀失败
        |
        +---- 成功
                |
                v
          更新消息状态为「待发送」
                |
                v
          发送 RabbitMQ
                |
                +---- ACK ----> 修改消息状态为已发送
                |
                +---- NACK/超时
                            |
                            v
                        保持待发送
                            |
                            v
                        定时补偿重发
```
> 先写消息表、后扣库存，确保每一笔 Redis 扣减都有可追溯的数据库记录，避免扣减后服务宕机导致库存凭空消失。
> ⚠️ **代价说明**：该顺序意味着**每个秒杀请求**（含库存不足/已限购）都会先 INSERT 一行 `mq_message`（随后置为失败）。10 万人抢购会写入 10 万行，DB 写压力可控但并非「Redis 先过滤后只有有效请求落库」。若想避免全量写，可改为「Redis 扣减成功后写消息表」，代价是 Redis 扣减成功到落表之间服务宕机时，该笔扣减无凭证，需靠对账发现并回补（Phase 1 人工对账）。**Phase 1 推荐保持先写消息表**，单机 500 QPS 下 DB 可承受。

### 1.4 三级可靠投递
生产者端最终采用三级可靠投递方案：
```text
本地消息表
+
RabbitMQ Publisher Confirm
+
定时任务补偿
```
完整流程：
```text
                    写入本地消息表（待扣库存）
                           |
                           v
                    Redis 扣库存成功
                           |
                           v
                    更新为待发送状态
                           |
                           v
                    RabbitMQ 发送
                           |
                 +---------+---------+
                 |                   |
                ACK                NACK/超时
                 |                   |
                 v                   v
             标记已发送          保持待发送
                                     |
                                     v
                                  定时扫描
                                     |
                                     v
                                  重新发送
```

---
## 2. Broker 服务端（避免节点宕机丢失）

### 2.1 元数据与消息强持久化
- Exchange / Topic、Queue 必须声明为 `Durable`（持久化），避免 Broker 重启后元数据丢失
- 消息的 Delivery Mode 必须设置为 `Persistent`（持久化存储），确保消息落盘
- 生产端声明与消费端声明需保持一致，避免因参数不一致导致队列声明失败

### 2.2 多副本强一致集群
不同 MQ 中间件的高可靠部署要求：
- **RabbitMQ**：必须使用 **Quorum Queue**（基于 Raft 协议），消息在写入多数派节点的磁盘后才向生产者返回 SUCCESS
- **RocketMQ / Kafka**：需开启同步双写（Sync Replication）+ 同步刷盘（Sync Flush），保证主从强一致

```text
              RabbitMQ Cluster
        Node1       Node2       Node3
          \           |           /
           \          |          /
              Quorum Queue
       (Raft 多数派写入磁盘后才返回 SUCCESS)
```
> Quorum Queue 详细部署见第三十五节《RabbitMQ 集群》。

---
## 3. 消费者端（避免消费中断/落库失败丢失）

### 3.1 手动 ACK 延后（Manual ACK）
严禁开启 Auto ACK。消费者拉取消息后进入处理逻辑，必须在 **MySQL 订单事务提交成功后**，再向 MQ 节点发送 ACK：
```text
RabbitMQ
    |
    v
Consumer
    |
    v
Redis 状态机幂等校验
    |
    v
MySQL 事务（订单落库）
    |
    +---- 事务提交成功 ----> ACK
    |
    +---- 事务回滚   ----> NACK（requeue=true）
```
> 落库成功才确认消息，避免消费者宕机或落库失败导致消息凭空丢失。

### 3.2 重试机制与死信队列（DLQ）
遭遇数据库死锁、网络抖动等临时性故障时，启用**指数退避重试**（如重试 3~5 次）；若达到上限仍落库失败，则将消息投递至死信队列（DLQ）并触发告警，以便人工介入或二次补偿，**决不直接丢弃（NACK / Reject without requeue）**。
```text
消费失败
   |
   v
重试 1（间隔 1s）
   |
   +---- 成功 ---> ACK
   |
   v
重试 2（间隔 5s）
   |
   +---- 成功 ---> ACK
   |
   v
重试 3（间隔 30s）
   |
   +---- 成功 ---> ACK
   |
   +---- 仍失败
            |
            v
        投递死信队列（DLQ）
            |
            v
        触发告警，人工介入或二次补偿
```

### 3.3 批量消费的 ACK 时机
批量刷库场景下同样遵循"事务提交后 ACK"原则（详见第二十九~三十一节）：
- **整批失败**：整批不 ACK，RabbitMQ 重新投递，多次失败送入 DLQ
- **部分失败**：使用 `INSERT ... ON DUPLICATE KEY UPDATE`，成功的消息正常 ACK，真正失败的消息单独 NACK 或送入重试队列，避免因单条异常导致整批反复重发

---
## 4. 兜底与异常自愈

### 4.1 定时任务对账补发
后台定时扫描本地消息表中**超时的 `待发送` 记录**，重新投递至 MQ：
```text
定时任务（分布式锁保证单实例执行）
        |
        v
扫描 status=待发送 AND next_retry_time <= NOW()
        |
        v
重新投递 MQ
        |
        +---- ACK ----> 更新为已发送
        |
        +---- 仍失败 --> retry_count++，按指数退避更新 next_retry_time
        |
        +---- 超过最大重试次数 --> 标记为发送失败，触发告警 + 库存回补
```
> 定时任务的并发安全控制见第五十一节。

### 4.2 全链路对账
除生产端补发外，还需建立全链路对账机制（详见第四十八节）：
- **本地消息表 vs MQ 消息**：校验消息无丢失
- **MQ 消费记录 vs 订单表**：校验消费消息全部落库
- **Redis 库存 vs MySQL 有效订单数**：校验库存扣减与订单数量匹配

### 4.3 下游幂等性配合
为应对重试导致的重复投递，消费端必须利用业务唯一标识（`userId + activityId + seckillItemId`）结合 **Redis 状态机 + MySQL UNIQUE KEY** 实现双重幂等，保证重发不引发重复创建订单（详见第十六节、第二十一~二十三节）：
```text
重复消息
   |
   v
Redis 状态机校验（快速过滤）
   |
   +---- SUCCESS ---> 直接 ACK
   |
   +---- 可处理
          |
          v
      MySQL INSERT
          |
          +---- 成功 -----------> 更新 Redis 为 SUCCESS ---> ACK
          |
          +---- UNIQUE 冲突 ----> 更新 Redis 为 SUCCESS ---> ACK
```

---
## 5. 全链路闭环总览
```text
[生产者]                  [Broker]                 [消费者]                 [兜底]
   |                         |                        |                       |
   v                         v                        v                       v
本地消息表双写          Durable + Persistent       手动 ACK 延后          定时对账补发
   |                         |                        |                       |
   v                         v                        v                       v
Publisher Confirm       Quorum Queue                事务提交后 ACK          全链路对账
   |                    (Raft 多数派)                  |                       |
   v                         |                        v                       v
ACK→已发送                  |                     重试 + DLQ               下游幂等配合
   |                         |                        |                       |
   +-------------------------+------------------------+-----------------------+
                             |
                             v
                     消息绝对不丢失
```

---
# 十四、本地消息表并发问题
本地消息表基于数据库。需要澄清：若采用「先写消息表、后扣库存」的顺序（见 1.3 节），**每个请求都会先写入一行** `mq_message`，Redis 竞争只决定该行后续流转为「待发送」还是「失败」——10 万人抢购会产生 10 万行写入（约 9.9 万行置为失败），DB 写压力为全量而非仅有效请求。
若需把 DB 写入量降到「有效请求数」，应改为「Redis 扣减成功后再写消息表」，代价是扣减到落表之间存在崩溃窗口，需靠对账回补（见 4.2 节）。
> 两种顺序二选一，本方案默认「先写消息表」，以正确性优先（Phase 1 单机 500 QPS 下 DB 可承受全量写）。

---
# 十五、MQ 重复消费
RabbitMQ 存在重复消费可能，例如：
```text
消费者收到消息
      |
      v
创建订单成功
      |
      X
ACK失败
      |
      v
RabbitMQ重新投递
```
同一个 messageId 可能被消费多次，因此必须保证消费幂等。

---
# 十六、Redis 幂等设计：状态机替代纯 SETNX
使用 messageId 作为 Redis 幂等 Key，采用**状态机模式**替代简单 SETNX，解决「处理失败后重发被误判为已消费」的漏洞。

Key 格式：
```text
seckill:order:{userId}:{activityId}:{seckillItemId}
```
状态枚举：
```text
PROCESSING = 1  处理中
SUCCESS    = 2  处理成功
FAILED     = 3  处理失败
```
设置命令：
```text
SET seckill:order:8888:1001:2001 1 NX EX 300
```
> NX 保证只有首次能设置成功，EX 设置过期时间，避免永久残留。
> ⚠️ **TTL 窗口**：若 DB 故障超过 TTL（30min），PROCESSING Key 过期后重投会重新 SETNX 成功，两个消费者可能同时处理同一 messageId——最终由 `order.uk_user_activity_item` 唯一键兜底，不会产生重复订单，但要意识到该并发窗口存在。

---
# 十七、第一次消费
```text
MQ消息
   |
   v
读取 messageId
   |
   v
Redis SETNX 设置为 PROCESSING
   |
   v
设置成功
   |
   v
进入批量队列，等待落库
   |
   v
MySQL 事务成功 --> 更新 Redis 状态为 SUCCESS
   |
   v
ACK
```

---
# 十八、重复消费
```text
MQ重复消息
   |
   v
读取 messageId
   |
   v
查询 Redis 状态
   |
   +---- SUCCESS ----> 直接 ACK
   |
   +---- PROCESSING --> 延迟重试，不 ACK
   |
   +---- FAILED ------> 删除旧 Key，重新进入处理流程
   |
   +---- 不存在 -------> 重新 SETNX 开始处理
```
> ⚠️ **重试上限（防止毒消息热循环）**：FAILED → 删 Key → 重投 → 再处理的循环必须设置重试上限（如消息重投 ≥3 次或 `mq_message.retry_count` 超限），否则遇到永久性失败（商品下架、`seckill_item` 不存在）会无限循环打爆队列/DB。超限后：置 `mq_message.status=3`（发送失败），触发告警与库存回补，**不再重投**。实现上可用 RabbitMQ `x-delivery-limit` 或消费端本地计数。

---
# 十九、为什么需要 TTL
如果不设置过期时间，幂等 Key 会永久存在，长期运行产生大量冗余 Key。
TTL 时长需覆盖：
```text
MQ最大重试时间
+
业务最大处理时间
+
批量刷库等待时间
```
建议设置为 5~30 分钟，根据业务重试周期调整。

---
# 二十、纯 SETNX 的局限与修正
**原方案问题：**
仅使用 SETNX + 创建订单，若 MySQL 异常回滚，Redis Key 仍然存在，后续重发会被直接判定为已消费，最终导致订单丢失。

**状态机方案解决思路：**
- 处理中状态不代表成功，重发时不会被直接丢弃
- 成功状态才视为已消费，可直接 ACK
- 失败状态允许重新处理，保证异常可恢复
- MySQL 唯一键作为最终兜底，形成完整闭环

---
# 二十一、业务表唯一约束
复用现有 `order` 订单主表扩展秒杀字段，使用「用户ID + 活动ID + 秒杀商品项ID」建立唯一索引，作为最终幂等兜底。
```sql
-- 在 order 表新增 3 个字段，标识秒杀属性
ALTER TABLE `order`
  ADD COLUMN `order_source`     TINYINT         NOT NULL DEFAULT 1 COMMENT '订单来源 1普通 2秒杀',
  ADD COLUMN `activity_id`      BIGINT UNSIGNED DEFAULT NULL       COMMENT '秒杀活动ID',
  ADD COLUMN `seckill_item_id`  BIGINT UNSIGNED DEFAULT NULL       COMMENT '秒杀商品项ID';

-- 增加唯一索引做幂等兜底（秒杀场景：同一用户 + 同一活动 + 同一秒杀商品只能下一单）
ALTER TABLE `order`
  ADD UNIQUE KEY `uk_user_activity_item` (`user_id`, `activity_id`, `seckill_item_id`);
```
> `seckill_item_id` 指向 `seckill_item.id`（SKU 维度秒杀商品项），与 `seckill_item.uk_activity_sku(activity_id, sku_id)` 对应。
> 普通订单 `order_source=1` 时，`activity_id` 与 `seckill_item_id` 均为 NULL，不受该唯一索引约束（MySQL 唯一索引允许多个 NULL 共存）。

---
# 二十二、数据库唯一约束实现幂等
第一次消费：
```text
INSERT 订单
      |
      v
成功
```
重复消费：
```text
INSERT 订单
      |
      v
UNIQUE KEY 冲突
      |
      v
DuplicateKeyException
      |
      v
判定订单已存在
      |
      v
ACK 消息
```
数据库保证：同一个用户在同一个活动中购买同一个商品，最多产生一笔订单。

---
# 二十三、最终幂等方案
采用「Redis 状态机幂等 + MySQL 唯一键兜底」的两层幂等架构。
```text
MQ消息
   |
   v
Redis 状态机校验
   |
   +---- SUCCESS ---> 直接 ACK
   |
   +---- 可处理
          |
          v
      MySQL INSERT
          |
          +---- 成功 ---> 更新 Redis 为 SUCCESS ---> ACK
          |
          +---- UNIQUE 冲突 ---> 更新 Redis 为 SUCCESS ---> ACK
```
- Redis 层：快速过滤重复消息，减少数据库压力
- MySQL 层：最终业务幂等兜底，保证数据绝对一致

---
# 二十四、MQ 消息积压
假设生产速度 10000 msg/s，消费速度 3000 msg/s，RabbitMQ 会产生大量积压。
解决方案：
```text
增加消费者实例
+
提高单消费者并发
+
批量刷库提升吞吐
+
上游动态限流
```

---
# 二十五、消费者批量刷库
消费者收到 MQ 消息后，不立即执行单条 INSERT，而是先进入批量缓冲区。
```text
RabbitMQ
    |
    v
消费者
    |
    v
Batch Queue
    |
    +---- 数量达到阈值
    |
    +---- 时间达到阈值
    |
    v
批量 INSERT
    |
    v
MySQL
```

---
# 二十六、批量刷库触发条件
采用数量阈值 + 时间阈值双触发机制。
例如：
```text
batchSize = 1000
flushInterval = 1秒
```
满足任意条件就刷库：
```text
buffer.size >= 1000
OR
距离上次刷库 >= 1秒
```

---
# 二十七、为什么使用双阈值
如果只使用数量阈值，低流量下数据可能长时间滞留在内存；
如果只使用时间阈值，高峰期可能积压大量数据。
双阈值兼顾吞吐与实时性，任意条件满足即执行刷库。

---
# 二十八、批量刷库流程
```text
                    RabbitMQ
                        |
                        v
                    Consumer
                        |
                        v
                  Batch Queue
                        |
             +----------+----------+
             |                     |
       数量 >= 1000            时间 >= 1s
             |                     |
             +----------+----------+
                        |
                        v
                  Batch INSERT
                        |
                        v
                      MySQL
                        |
                        v
                    事务提交
                        |
                        v
更新 Redis 幂等状态 + 批量 ACK
```

---
# 二十九、批量刷库不能提前 ACK
错误方式：消息入内存队列后立即 ACK。
若服务器宕机，内存队列数据全部丢失，MQ 已确认消费成功，但 MySQL 没有订单，最终导致订单丢失。

---
# 三十、正确 ACK 时机
必须在 MySQL 事务提交成功后，再批量确认 MQ 消息。
```text
RabbitMQ
    |
    v
Consumer
    |
    v
Batch Queue
    |
    v
Batch INSERT
    |
    v
MySQL 事务提交成功
    |
    v
批量 ACK
```
> 落库成功才确认消息，保证消息不丢。

---
# 三十一、批量刷库失败处理
## 整批失败
若 MySQL 整体异常、事务回滚，则整批消息不 ACK，RabbitMQ 后续重新投递。
重试多次仍失败则送入死信队列，人工介入。

## 部分失败
若仅部分消息触发唯一键冲突或数据异常，不能整批回滚：
- 使用 `INSERT ... ON DUPLICATE KEY UPDATE` 语法，冲突则视为成功
- 批量执行后逐条匹配处理结果
- 成功的消息正常 ACK
- 真正失败的消息单独 NACK 或送入重试队列
- 避免因单条异常导致整批反复重发

---
# 三十二、批量刷库与幂等
批量 INSERT 仍然依赖两层幂等保障：
- Redis 状态机提前过滤绝大多数重复消息
- MySQL 唯一键处理漏网的重复消息
  示例 SQL（复用 `order` 表，秒杀订单 `order_source=2`）：
```sql
INSERT INTO `order`
(order_no, user_id, order_state, total_money, pay_money, order_source, activity_id, seckill_item_id, create_time)
VALUES
(...), (...), (...)
ON DUPLICATE KEY UPDATE update_time = NOW();
```
> ⚠️ **order + order_item 对的幂等**：`order_item` 没有唯一键，重复消息时 `order` 走 ON DUPLICATE 变 no-op，但 `order_item` 仍会重复插入。批量幂等必须对「order + order_item 对」整体处理：命中唯一键冲突后先查回已存在的 `order_id`，再决定是否跳过 `order_item`，避免明细翻倍。

---
# 三十三、消费者集群
采用多消费者集群部署，共同消费同一个队列。
```text
                     RabbitMQ
                         |
        +----------------+----------------+
        |                |                |
    Consumer1        Consumer2        Consumer3
        |                |                |
    BatchQueue       BatchQueue       BatchQueue
        |                |                |
        +----------------+----------------+
                         |
                         v
                       MySQL
```
可根据 MQ 积压量、MySQL 负载动态调整消费者数量。

---
# 三十四、不能无限增加消费者
消费者数量过多会导致 MySQL 连接数飙升、CPU/IO 过载、锁竞争加剧。
原则：
> MQ 消费速度不能无限追求，而应该以 MySQL 能稳定承载为上限。
上游需配合动态限流，避免消费速度超过数据库承载能力。

---
# 三十五、RabbitMQ 集群
生产环境使用 RabbitMQ Cluster，推荐采用 Quorum Queue，保证多副本与故障自动恢复。
```text
              RabbitMQ Cluster
        Node1       Node2       Node3
          \           |           /
           \          |          /
              Quorum Queue
```
同时配置死信队列与延迟队列，分别处理失败消息与支付超时。

---
# 三十六、Redis 故障转移
Redis 使用 Cluster 模式，主从结构：
```text
Master1 -> Slave1
Master2 -> Slave2
Master3 -> Slave3
```
Master 故障后，Cluster 自动将对应 Slave 提升为新 Master，实现自动故障转移。

---
# 三十七、Redis 主从切换库存回退防护
## 问题现象
Master 扣减库存后数据未同步到 Slave 时宕机，Slave 提升为 Master 后库存回退，可能导致超卖。

## 防护方案
1. **同步等待增强**：关键场景使用 `WAIT` 命令等待从节点同步，降低回退概率
2. **MySQL 库存兜底**：创建订单时执行 `UPDATE 商品库存表 SET stock = stock - 1 WHERE id = ? AND stock > 0`，影响行数为 0 则判定超卖，取消订单并回补 Redis
3. **分钟级对账**：定时对比 Redis 库存与 MySQL 有效订单数，发现偏差自动告警与校准
4. **最终以 MySQL 为准**：Redis 库存仅用于高并发入口，业务事实以数据库为准

---
# 三十八、库存补偿闭环
## 触发场景
- 订单创建失败
- 支付超时自动取消
- 用户主动取消订单
- 退款完成
- 对账发现库存不一致

## 补偿流程
1. 生成库存补偿消息，携带唯一业务流水号
2. 通过 Lua 脚本执行库存回补，校验订单状态后再增加库存
3. 记录库存补偿流水，防止重复回补（`seckill_stock_compensate` 表，见分阶段计划 Phase 2）
4. 回补操作纳入对账体系

> ⚠️ **补偿与限购/幂等 Key 的语义**：补偿只回补库存，**不回退** `seckill:user:*` 限购 Key 与 `seckill:order:*` 幂等 Key。因此超时取消后回补的库存可被**其他用户**抢，但**本人不能重抢**（限购 Key 仍在）。若要允许本人重抢，需在补偿流程里一并删除对应限购/幂等 Key（需评估重复下单风险，由唯一键兜底）。此语义需与产品确认。

---
# 三十九、MySQL 最终库存校验
订单创建时可执行库存扣减校验，作为超卖最后一道防线（直接复用 `seckill_item.seckill_stock`）：
```sql
UPDATE `seckill_item`
SET `seckill_stock` = `seckill_stock` - 1
WHERE `id` = ?
  AND `seckill_stock` > 0;
```
- `affected_rows = 1`：扣库存成功
- `affected_rows = 0`：库存不足，订单回滚

> `id` 即 `seckill_item.id`（秒杀商品项主键），与 `order.seckill_item_id` 关联。
> 若 Redis 预扣成功但 MySQL 最终扣减失败，需通过补偿机制恢复 Redis 库存。

---
# 四十、秒杀防刷与风控
## 基础限流
- IP 限流：基于 Redis 计数器，单位时间内超过阈值直接拒绝
- 用户限流：限制单用户秒杀请求频率

## 进阶防护
- 秒杀 Token：活动开始前获取，请求必须携带有效 Token
- 验证码：热门秒杀增加图形/滑块验证码，降低机器请求
- 设备指纹：识别异常设备，拦截刷单账号
- 行为风控：识别异常操作路径，拦截黄牛批量请求

---
# 四十一、完整核心链路
```text
                              用户
                               |
                               v
                         网关/限流/风控防刷
                               |
                               v
                         秒杀服务集群
                    （校验活动时间窗口）
                               |
                               v
                         写入本地消息表
                               |
                               v
                       +----------------+
                       | Redis Cluster  |
                       |                |
                       | Lua原子扣库存  |
                       | 用户限购       |
                       +----------------+
                               |
                        扣库存成功
                               |
                               v
                  更新本地消息表为待发送
                               |
                               v
                       RabbitMQ 集群
                               |
                               v
                       订单消费者集群
                               |
                               v
                     Redis 状态机幂等校验
                               |
                               v
                         Batch Queue 缓冲
                               |
                  +------------+------------+
                  |                         |
             数量 >= 阈值               时间 >= 阈值
                  |                         |
                  +------------+------------+
                               |
                               v
                         MySQL 批量 INSERT
                               |
                               v
                      UNIQUE KEY 幂等兜底
                               |
                               v
                         事务提交成功
                               |
                               v
                     更新 Redis 幂等状态
                               |
                               v
                              批量 ACK
```

---
# 四十二、异常流程
## 1. Redis 扣库存成功，MQ 发送失败
```text
Redis 扣库存成功
      |
      v
本地消息表待发送
      |
      X
MQ 发送失败
      |
      v
定时任务扫描
      |
      v
重新发送 MQ
      |
      v
超过最大重试 → 死信队列 + 库存回补
```

## 2. MQ 重复消费
```text
MQ 消息
   |
   v
Redis 状态机校验
   |
   +---- SUCCESS ---> 直接 ACK
   |
   +---- 可处理
          |
          v
        MySQL 落库
```

## 3. Redis 幂等设置成功，MySQL 失败
```text
Redis 设置为 PROCESSING
      |
      v
MySQL 事务失败
      |
      v
事务回滚
      |
      v
更新 Redis 为 FAILED
      |
      v
不 ACK，MQ 重发
      |
      v
重发时识别 FAILED，允许重新处理
```
> ⚠️ 该「重发 → 重新处理」必须设重试上限（见第十八节），防止永久性失败时无限热循环。

## 4. Redis 主从切换库存回退
```text
主从切换 → 库存回退
      |
      v
Redis 继续扣减 → 可能超卖
      |
      v
MySQL 库存校验拦截
      |
      v
订单创建失败 → 回补 Redis 库存
      |
      v
对账任务校准库存
```

---
# 四十三、核心数据表
本方案遵循「能复用就不新建」原则，仅 `mq_message` 为新增表，其余表均复用 `sql/init.sql` 已有结构并扩展秒杀字段。

## 1. 本地消息表（**新建**）
```sql
CREATE TABLE `mq_message` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `message_id`      VARCHAR(128)    NOT NULL                COMMENT '业务唯一ID（userId:activityId:seckillItemId）',
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `activity_id`     BIGINT UNSIGNED NOT NULL,
  `seckill_item_id` BIGINT UNSIGNED NOT NULL               COMMENT '秒杀商品项ID，关联 seckill_item.id（SKU 维度）',
  `spu_id`          BIGINT UNSIGNED DEFAULT NULL           COMMENT '冗余 SPU 维度，便于对账',
  `sku_id`          BIGINT UNSIGNED DEFAULT NULL           COMMENT '冗余 SKU 维度，便于对账',
  `quantity`        INT             NOT NULL DEFAULT 1     COMMENT '购买数量',
  `status`          TINYINT         NOT NULL DEFAULT 0      COMMENT '0-待扣库存 1-待发送 2-已发送 3-发送失败 4-已完成',
  `retry_count`     INT             NOT NULL DEFAULT 0      COMMENT '重试次数',
  `next_retry_time` DATETIME        DEFAULT NULL            COMMENT '下次重试时间',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_status_next_time` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 本地消息表（秒杀下单可靠投递凭证）';
```
> ⚠️ 该表已在 `sql/init.sql` / `sql/_apply_seckill.sql` 落库，但字段为 `product_id`（SPU 维度）。Phase 1 需迁移为 `seckill_item_id`（见 1.1 节迁移说明）。

## 2. 秒杀订单表（**复用 `order` + `order_item`，扩展秒杀字段**）
不新建 `seckill_order` 表，直接在订单主表上扩展 3 个字段，秒杀订单与普通订单共用同一张订单表，通过 `order_source` 区分。

```sql
-- 在 order 表新增 3 个字段，标识秒杀属性
ALTER TABLE `order`
  ADD COLUMN `order_source`     TINYINT         NOT NULL DEFAULT 1 COMMENT '订单来源 1普通 2秒杀',
  ADD COLUMN `activity_id`      BIGINT UNSIGNED DEFAULT NULL       COMMENT '秒杀活动ID',
  ADD COLUMN `seckill_item_id`  BIGINT UNSIGNED DEFAULT NULL       COMMENT '秒杀商品项ID';

-- 增加唯一索引做幂等兜底（秒杀场景：同一用户 + 同一活动 + 同一秒杀商品只能下一单）
ALTER TABLE `order`
  ADD UNIQUE KEY `uk_user_activity_item` (`user_id`, `activity_id`, `seckill_item_id`);
```

字段说明：
| 字段 | 类型 | 说明 |
|---|---|---|
| `order_source` | TINYINT | 1=普通订单（默认），2=秒杀订单 |
| `activity_id` | BIGINT UNSIGNED | 秒杀活动ID，普通订单为 NULL |
| `seckill_item_id` | BIGINT UNSIGNED | 关联 `seckill_item.id`，普通订单为 NULL |

订单明细复用 `order_item` 表，秒杀订单写入时：
- `order_item.sku_id` = `seckill_item.sku_id`
- `order_item.spu_id` = `seckill_item.spu_id`
- `order_item.cur_price` = `seckill_item.seckill_price`（秒杀价快照）
- `order_item.price` = SKU 原价（用于展示优惠幅度）

> 唯一索引 `uk_user_activity_item` 中三个字段对普通订单均为 NULL（`activity_id`、`seckill_item_id`）或非秒杀场景（`order_source=1`），MySQL 唯一索引允许多个 NULL 共存，因此不影响普通订单的多次下单。

## 3. 商品活动库存表（**复用 `seckill_item`，无需新建**）
原方案的 `seckill_product_stock` 表功能已被 `seckill_item` 完全覆盖，不再单独建表。

`seckill_item` 现有结构（来自 `sql/init.sql`）：
```sql
CREATE TABLE `seckill_item` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `activity_id`     BIGINT UNSIGNED NOT NULL,
  `spu_id`          BIGINT UNSIGNED NOT NULL,
  `sku_id`          BIGINT UNSIGNED NOT NULL,
  `seckill_price`   DECIMAL(10,2)   NOT NULL                    COMMENT '秒杀价',
  `seckill_stock`   INT             NOT NULL DEFAULT 0           COMMENT '秒杀库存（独立于 SKU 原库存）',
  `limit_per_user`  INT             NOT NULL DEFAULT 1           COMMENT '每人限购',
  `sort_order`      INT             NOT NULL DEFAULT 0,
  `status`          TINYINT         NOT NULL DEFAULT 1           COMMENT '0下架 1上架',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_sku` (`activity_id`, `sku_id`),
  KEY `idx_activity_sort` (`activity_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动商品项';
```

字段映射关系：
| 原方案 `seckill_product_stock` | 复用 `seckill_item` | 说明 |
|---|---|---|
| `id` | `id` | 秒杀商品项主键，即 `order.seckill_item_id` |
| `activity_id` | `activity_id` | 秒杀活动ID |
| `product_id` | `spu_id` + `sku_id` | SPU/SKU 维度更精确 |
| `stock` | `seckill_stock` | 秒杀库存，独立于 SKU 原库存 |
| `uk_activity_product(activity_id, product_id)` | `uk_activity_sku(activity_id, sku_id)` | SKU 维度唯一约束 |
| —— | `seckill_price` | 额外提供秒杀价（原方案无） |
| —— | `limit_per_user` | 额外提供每人限购（原方案无） |
| —— | `status` | 额外提供上下架控制（原方案无） |

> `seckill_item` 比 `seckill_product_stock` 多出 `seckill_price`、`limit_per_user`、`status` 等字段，完全覆盖原方案功能且更完整。Redis 库存预热的 Key 统一为 `seckill:stock:{activityId}:{seckillItemId}`（见第四十四节；Phase 3 集群再加 `{activityId}` Hash Tag）。

---
# 四十四、核心 Redis Key
```text
# 秒杀库存（SKU 维度）
seckill:stock:{activityId}:{seckillItemId}
# 商品项元数据缓存（预热时写入：spu_id/sku_id/秒杀价/原价/limit_per_user，供入口读取拼消息，TTL=活动剩余时长）
seckill:item:{seckillItemId}
# 用户限购记录（SKU 维度，值=累计已购数量）
seckill:user:{activityId}:{seckillItemId}:{userId}
# 消费幂等（状态机）
seckill:order:{userId}:{activityId}:{seckillItemId}
# 用户限流
seckill:rate:{userId}
# IP 限流
seckill:rate:ip:{ip}
```
示例：
```text
seckill:stock:1001:2001
seckill:user:1001:2001:8888
seckill:order:8888:1001:2001
```
> ⚠️ **前缀约定**：为可读性本文省略前缀，**实现时统一在键首加 `mall:` 前缀**（`MallConstants.REDIS_PREFIX`），避免与项目其他业务键冲突；模板常量统一定义在 `MallConstants`，勿散落硬编码。Phase 3 集群场景，库存/限购 Key 需加 `{activityId}` Hash Tag（见第十节）。

---
# 四十五、核心参数建议
| 参数              |          建议值 | 说明          |
| --------------- | -----------: | ----------- |
| Redis Cluster节点 |         3主3从 | 高可用部署     |
| MQ节点            |            3 | RabbitMQ集群  |
| MQ队列类型        | Quorum Queue | 高可靠多副本    |
| Batch Size      |     500~2000 | 初始建议 1000  |
| Flush Interval  |           1秒 | 低流量最大等待  |
| Redis 幂等 TTL    |       5~30分钟 | 覆盖最大重试周期 |
| MQ 最大重试次数    |         3~5次 | 超过进入死信    |
| 消费者数量         |         动态调整 | 根据 MySQL 承载 |
| 库存分片数         |         4~16片 | 热点商品按需拆分 |
| 支付超时时长       |        5~15分钟 | 根据业务配置    |

---
# 四十六、最终方案总结
本系统采用：
```text
Redis Cluster
+
RabbitMQ Cluster
+
MySQL
+
Lua 原子脚本
+
本地消息表
+
Redis 状态机幂等
+
MySQL UNIQUE KEY
+
批量刷库 + 部分失败处理
+
MQ 重试 + 死信队列
+
库存补偿闭环
+
全链路对账
+
多级降级熔断
```
形成完整的生产级秒杀系统。

---
## 各组件职责
### 1. Redis
负责：
```text
高并发流量承接
原子库存竞争
用户限购校验
快速幂等过滤
多级限流计数
```

### 2. RabbitMQ
负责：
```text
异步下单解耦
流量削峰填谷
消息可靠投递
消息重试机制
死信异常处理
延迟超时处理
```

### 3. MySQL
负责：
```text
订单持久化存储
业务数据一致性
唯一索引幂等兜底
本地消息表凭证
最终库存校验
```

### 4. 统一业务 ID
全链路统一使用：
```text
messageId = userId + activityId + seckillItemId
```
该 ID 同时作为 MQ 业务 ID、本地消息表唯一键、Redis 幂等键、订单业务唯一标识，保证整条链路身份统一。

---
# 四十七、最终设计原则
```text
Redis 负责快
RabbitMQ 负责削峰
MySQL 负责最终一致性
对账负责发现偏差
补偿负责修复异常
```

完整链路：
```text
高并发请求
      |
      v
网关限流风控
      |
      v
写入本地消息表
      |
      v
Redis Cluster Lua 原子扣库存
      |
      v
生成业务 messageId
      |
      v
更新消息表状态
      |
      v
RabbitMQ 可靠投递
      |
      v
订单消费者集群
      |
      v
Redis 状态机幂等校验
      |
      v
批量缓冲双阈值触发
      |
      v
MySQL 批量落库
      |
      v
唯一键最终兜底
      |
      v
事务提交成功
      |
      v
更新幂等状态 + 批量 ACK
```

最终实现：
> **Redis Cluster 抗高并发 + Lua 保证库存操作原子性 + 本地消息表保证消息可追溯 + RabbitMQ Cluster 负责削峰和可靠投递 + Redis 状态机实现安全幂等 + MySQL UNIQUE KEY 保证最终业务幂等 + 批量刷库提升数据库吞吐 + 重试/死信/补偿保证异常自愈 + 全链路对账保证数据一致。**

---
# 四十八、全链路对账机制
## 对账维度
1. **Redis 库存 vs MySQL 有效订单数**：校验库存扣减与订单数量匹配
2. **本地消息表 vs MQ 消息**：校验消息无丢失
3. **MQ 消费记录 vs 订单表**：校验消费消息全部落库

## 对账频率
- 分钟级：核心指标轻量对账，异常立即告警
- 小时级：全量深度对账，自动修复小偏差
- 活动结束：全量最终对账，生成对账报告

## 偏差处理
- 少卖（Redis 扣了没订单）：回补 Redis 库存或补发订单
- 超卖（订单数 > 初始库存）：触发告警，人工介入处理
- 所有偏差操作记录流水，留痕可追溯

---
# 四十九、支付生命周期与超时取消
1. 订单创建时设置支付超时时间
2. 发送延迟消息到 RabbitMQ 死信队列
3. 延迟到期后检查订单状态
4. 未支付则自动取消订单，回补库存
5. 已支付则忽略，不做处理

---
# 五十、分级降级与熔断策略
## Redis 故障降级
- 降级为同步数据库扣库存
- 开启强限流，保护数据库
- 只保留核心秒杀能力，关闭非核心功能

## MQ 故障降级
- 消息发送超时阈值内持续重试
- 超过阈值自动回滚 Redis 库存，返回秒杀失败
- 极端情况切换为同步下单模式，配合强限流

## MySQL 高负载降级
- 自动调小批量大小、拉长刷库间隔
- 上游限流降低生产速度
- 暂停非核心写操作，保障核心订单写入

---
# 五十一、定时任务并发安全控制
本地消息表补偿、对账等定时任务多实例部署时需避免并发重复执行：
1. 基于 Redis 分布式锁，同一时间只有一个实例执行
2. 采用分片扫描策略，每个实例处理固定 ID 区间
3. 处理前校验状态，避免重复处理同一条记录
4. 控制扫描频率与批量大小，避免对数据库造成压力
```