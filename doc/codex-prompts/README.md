# Codex 开发提示词包 —— 秒杀 Phase 1（mall-seckill-service）

> 给 OpenAI Codex CLI 用的分任务开发提示词。目标：在 `E:/zhiwu-mall` 仓库里把 `mall-seckill-service`（现为空壳）实现为可运行的秒杀服务。
> **按顺序逐条投喂，每条自测通过后再喂下一条。**

## 使用方式

在仓库根目录启动 Codex，逐条粘贴提示词文件内容（或用 `@` 引用文件）：

```bash
cd E:/zhiwu-mall
codex
# 粘贴 00-项目上下文与开发总纲.md → 粘贴 01-工程骨架与配置.md → ... → 07
```

每条提示词都自包含「目标 / 前置 / 规格 / 约束 / 完成标准」，Codex 可直接照写。

## 顺序与依赖

| 顺序 | 文件 | 交付 | 依赖 |
|---|---|---|---|
| 1 | 00-项目上下文与开发总纲.md | 全局上下文（不产出代码，建立认知） | — |
| 2 | 01-工程骨架与配置.md | 可编译可启动的空壳 + dev 配置 | 00 |
| 3 | 02-数据层与基础设施.md | entity/mapper/常量/Lua/MQ/安全装配 | 01 |
| 4 | 03-秒杀入口与本地消息表.md | execute/result + 预热 + 本地消息表 + 发 MQ | 02 |
| 5 | 04-订单创建与MQ消费.md | MQ 消费者 + 建单 + 双层幂等 | 03 |
| 6 | 05-超时取消与库存回补.md | 延迟取消 + 回补 + 补偿任务 + 内部回补接口 | 04 |
| 7 | 06-order-service协作改造.md | order-service Feign + 用户取消回补 | 05 |
| 8 | 07-构建验证与验收.md | 编译/启动/冒烟/验收清单 | 全部 |

## 全局约定（所有任务必须遵守）

- **业务主键**：`messageId = userId:activityId:seckillItemId`（SKU 维度），贯穿 Redis 幂等 Key、`mq_message.message_id`、`order.uk_user_activity_item`。
- **接口契约**：所有端点返回 `Result<T>` = `{ code, message, data }`；成功 `Result.success(data)`，业务异常 `throw new BizException(ResultCode.X)`。
- **Redis Key** 统一加 `mall:` 前缀；**库存/限购 Key 必须用 `StringRedisTemplate`**（`RedisTemplate` 的 value 是 JSON 序列化，Lua 的 `DECRBY/INCRBY/GET` 需要纯整型，勿混用）。
- **禁止对 `order`/`order_item` 表做任何 DDL**（与 order-service 共用物理表，只做字段映射）。
- **参考文档**：`doc/秒杀方案分阶段实施计划.md`（权威，尤其 3.4 关键设计与 3.4.5 工程骨架）、`doc/基于Redis和MQ实现秒杀订单加购.md`（终态设计）。
- **库表字段**：以 `sql/init.sql` 为准（已含秒杀扩展字段）。
- 端口 8089；**调试时勿同时运行 shopkeeper-agent 的 TEI 容器**（同端口）。

## 验收主线（07 会展开）

500 QPS 无超卖 / 无重复订单 / 无丢消息，P99 < 800ms。每条任务的「完成标准」是对应验收项的推进。
