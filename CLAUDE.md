# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`zhiwu-mall` is a Spring Boot 3.5.14 + Spring Cloud 2025.0.0 + Spring Cloud Alibaba 2023.0.1.2 microservices e-commerce system. Java 17, Maven multi-module (`packaging=pom`). All services register with Nacos and sit behind a Spring Cloud Gateway (WebFlux).

Three frontend/adjacent codebases exist alongside the Java services:
- **`uniapp-shop-vue3-ts/`** — uni-app + Vue 3 + TypeScript + Pinia 微信小程序端（可编译 H5/App）。基于第三方「小兔鲜儿」模板，但 `http.ts` 已改造为适配本项目 `{ code, message, data }` 契约。
- **`frontend-admin/`** — Vue 3 + Vite + Naive UI 管理后台（Vue 3 + Naive UI + ECharts），与 `mall-admin-service` 深度对接。
- **`shopkeeper-agent/`** — Python/FastAPI + LangGraph 的 Text-to-SQL 智能客服 agent，由 `mall-ai-service` 做 SSE 透传代理。见「AI Customer Service」一节。

**Current state: most business services are fully implemented** (see table below). Skeleton/placeholder areas: `mall-search-service` (no ES), `mall-auth-service` SMS login (mock). `mall-seckill-service` **runtime is implemented (Phase 1 完成)** — Redis 扣库存 + MQ 下单闭环已落地并压测通过（见 「Seckill」）。

## Build & Run

```bash
# Build everything (from repo root)
mvn -f E:/zhiwu-mall/pom.xml clean package -DskipTests

# Build a single service
mvn -f E:/zhiwu-mall/mall-product-service/pom.xml clean package -DskipTests

# Run a single service (must cd into the module dir for spring-boot:run)
cd E:/zhiwu-mall/mall-product-service && mvn spring-boot:run
```

No tests exist (no `src/test/` in any module) and there is no lint/checkstyle setup. `mvn test` will run nothing.

### ⚠️ Dev profile step (required before running)

Real credentials (MySQL/RabbitMQ/JWT/WeChat) are **not** committed. Each service that needs them ships an `application-dev.yml.example` template; the real `application-dev.yml` is gitignored. **Copy the template and fill in real values** for every service you intend to run:

```bash
cp E:/zhiwu-mall/mall-auth-service/src/main/resources/application-dev.yml.example \
   E:/zhiwu-mall/mall-auth-service/src/main/resources/application-dev.yml
# repeat for mall-user-service, mall-product-service, mall-order-service,
# mall-marketing-service, mall-admin-service, mall-gateway-service
```

`mall-search-service`, `mall-ai-service` have no dev profile (no DB/Redis/MQ creds needed). **`mall-seckill-service`（开发秒杀时）需新增 dev profile**（MySQL/Redis/RabbitMQ 连接，见「Seckill」节）。The default profile is `dev` (`SPRING_PROFILES_ACTIVE` env overrides). The gateway requires a `mall.jwt.secret` ≥ 32 bytes and must match the secret services use to sign/verify tokens.

### Infrastructure (start before any service)

```bash
docker compose -f E:/zhiwu-mall/docker-compose.yml up -d   # Nacos + Sentinel dashboard ONLY
```

`docker-compose.yml` provides **only** Nacos (8848/9848/9849) and Sentinel dashboard (8858). **MySQL, Redis, and RabbitMQ are locally installed services, not containers.** Required local infra:

- **Nacos** `127.0.0.1:8848` — service discovery
- **Sentinel Dashboard** `localhost:8858` — referenced only by `mall-marketing-service`
- **MySQL** `localhost:3306`, user `root` / `123456` — **single database `mall`**, initialized with `sql/init.sql` (now includes seckill tables). The AI agent also needs a `meta` database (from `shopkeeper-agent/docker/mysql/meta.sql`).
- **Redis** `127.0.0.1:6379`, no password, database `1`
- **RabbitMQ** `localhost:5672`, user `admin` / `123456`, vhost `/mall`. Must have the **`rabbitmq_delayed_message_exchange` plugin** installed (order timeout cancel uses a delayed exchange).

## Module Layout

Root POM manages Spring Cloud BOMs and the 8 internal `mall-common-*` versions.

`mall-common/` — shared starters every business service composes (parent POM pulls in Lombok globally):
- `mall-common-core` — `Result<T>`, `ResultCode`/`ErrorCode`, `BizException`/`UnauthorizedException`, `PageQuery`/`PageResult`, `MallConstants`, `HttpClientUtil`
- `mall-common-web` — `GlobalExceptionHandler` (`@RestControllerAdvice`), `CorsConfig`, `LoginUserContext`. **Servlet-based — do not add to the gateway.**
- `mall-common-security` — `JwtTokenService` (jjwt HMAC-SHA256), `TokenAuthInterceptor`, `JwtStpLogic` (SaToken + JWT), `SaTokenProperties`. Only pulled in by `mall-user-service` and `mall-admin-service` today.
- `mall-common-mybatis` — `BaseEntity` (id/createTime/updateTime), MyBatis-Plus autoconfig
- `mall-common-redis` — `RedisConfig`: String keys + `GenericJackson2JsonRedisSerializer`
- `mall-common-rabbitmq` — single `Jackson2JsonMessageConverter` bean
- `mall-common-feign` — `FeignConfig` (copies `Authorization`/`source-client` headers) + `FeignAuthHolder` (ThreadLocal)
- `mall-common-oss` — `OssController`/`OssService`/`AliOssProperties`, auto-configured via `@ConditionalOnProperty(alioss.bucket-name)`

Business services (each `@EnableDiscoveryClient` + `@SpringBootApplication`):

| Service | Port | Status | Notes |
|---|---|---|---|
| `mall-gateway-service` | 8080 | Implemented | Routes + `AuthGlobalFilter` (JWT 本地验签 + 白名单) |
| `mall-user-service` | 8081 | **Fully implemented** | Address, Cart, User profile/avatar (`com.hyf.malluserservice`, flat) |
| `mall-order-service` | 8082 | **Fully implemented** (deepest) | Order CRUD + Pay (WeChat/Mock), MQ 延迟超时取消; `OrderApplicationService` 642 行 |
| `mall-marketing-service` | 8083 | Implemented, narrow | Coupon only (`/coupons/**`); Sentinel; 秒杀/拼团/积分未做 |
| `mall-product-service` | 8084 | **Fully implemented** (DDD) | Category/Home/Hot/Product; home/seckill read paths |
| `mall-auth-service` | 8085 | Mostly complete | WeChat flows done; SMS mock/`smsLogin` throws |
| `mall-search-service` | 8086 | **Skeleton** | Only `/search/health`; ES not wired |
| `mall-ai-service` | 8087 | Implemented | Stateless SSE proxy to shopkeeper-agent |
| `mall-admin-service` | 8088 | **Fully implemented** | 6 controllers: auth/banner/product/sales/seckill/user CRUD + dashboards |
| `mall-seckill-service` | 8089 | **Fully implemented** | 秒杀入口(Redis 原子预扣)/预热/本地消息表/MQ 消费/订单创建/超时取消/库存回补/在途补偿 |

> ⚠️ `mall-seckill-service` (8089) shares a port with the shopkeeper-agent's TEI embedding container (also 8089 in its `docker-compose.yaml`) — they cannot run simultaneously.

## Seckill (active work area)

The seckill **schema is landed and the Phase 1 runtime is implemented**（Redis 原子预扣 + MQ 下单闭环）。Read `doc/秒杀方案分阶段实施计划.md` first — it supersedes details in `doc/基于Redis和MQ实现秒杀订单加购.md` (the 终态 target design)。性能压测见 `doc/秒杀压测结果-2026-08-13.md`。

### Schema (already applied to `sql/init.sql` and incrementally in `sql/_apply_seckill.sql`)

- `seckill_activity` — 活动（启停时间、enabled）
- `seckill_item` — **SKU 维度**（`seckill_price`/`seckill_stock`/`limit_per_user`, `uk_activity_sku(activity_id, sku_id)`）。替代原方案的 `seckill_product_stock`。
- `order` 扩展：`order_source`(1普通/2秒杀)、`activity_id`、`seckill_item_id` + 唯一索引 `uk_user_activity_item(user_id, activity_id, seckill_item_id)`（幂等）。秒杀订单直接写 `order`，**不新建** `seckill_order` 表。
- `mq_message` — 本地消息表（可靠投递凭证），状态机 0待扣库存→1待发送→2已发送→3发送失败→4已完成，`uk_message_id` 幂等。

### Key design decisions (from the plan doc)

- **业务主键统一 SKU 维度**：`messageId = userId:activityId:seckillItemId`，贯穿 Redis 幂等 Key、`mq_message.message_id`、`order.uk_user_activity_item`。
- **订单状态共用** `order.order_state`（1待付款…6已取消），不引入独立状态机。
- **Redis Key**（plan doc 为可读性省略前缀，**实现时统一加 `mall:` 前缀**，见 `MallConstants.REDIS_PREFIX`）：`seckill:stock:{activityId}:{seckillItemId}`、`seckill:item:{seckillItemId}`（商品项元数据缓存）、`seckill:user:{activityId}:{seckillItemId}:{userId}`、`seckill:order:{userId}:{activityId}:{seckillItemId}`（状态机 1PROCESSING/2SUCCESS/3FAILED，TTL 30min）、`seckill:activity:{activityId}`（活动结束时间戳缓存，入口校验免查库）、`seckill:inflight:{messageId}` + `seckill:inflight:index`（在途扣减标记/索引，崩溃补偿用）。
- **Lua 原子扣减**：单 Key 扣库存 + 用户限购校验 + 写在途标记（KEYS: stock + user + inflight + inflight-index，ARGV: 限购 TTL / 数量 / 限购 / messageId）。
- **MQ 拓扑**：`seckill.exchange`(Direct, durable) / `seckill.order.queue`(durable)，Publisher Confirm + 手动 ACK；延迟交换机 `seckill.delay.exchange`(`x-delayed-message`) 做支付超时取消。
- **入口热路径（性能关键）**：`execute` 先做 Redis 原子预扣，**只有扣减成功的请求才落 `mq_message` + 发 MQ**；被拒请求（库存不足/限购）在 Redis 阶段直接返回、不写 MySQL。活动与商品元数据走 Redis 缓存；`mq_message` 直接落 `status=1`(待发送) 并预留 60s 发送宽限（原 0→1 两步合并为一步）。「已扣库存但未落库」的崩溃窗口由 `recoverOrphanInflightDeducts` 定时任务回收（回补 Redis 库存 + 限购）。
- **Phase 1 服务边界**（plan 规定，2026-08-13 调整为集中式）：秒杀运行时**全部集中在 `mall-seckill-service`(8089)** —— 入口/预热/本地消息表(`mq_message`)/MQ 消费/订单创建/超时取消/库存回补；跨服务仅一处：order-service 取消秒杀订单(`order_source=2`)时 Feign 调 seckill-service `/internal/**` 回补。Phase 1 仅后端，前端秒杀页留到 Phase 2。
- **验收指标（Phase 1）**：单机 500 QPS 无超卖/无丢消息/无重复订单，P99 < 800ms。
- **性能配置**：`application.yml` 已配 HikariCP `maximum-pool-size: 50`（默认 10 是瓶颈）、RabbitMQ 监听 `prefetch: 50` + `concurrency: 5`(max 10)，避免 DB 连接池耗尽与单线程逐条消费成为吞吐瓶颈。

> ✅ **架构张力已解决（2026-08-13）**：秒杀运行时就在网关路由的 `mall-seckill-service`(8089) 实现（原拆 marketing+order 的方案已废弃），现已完整落地（入口/预热/本地消息表/MQ 消费/订单创建/超时取消/库存回补/在途补偿）。8089 与 TEI 容器端口冲突，调试秒杀时勿同时运行 shopkeeper-agent 的 TEI。

## Authentication (`mall-auth-service`)

`mall-auth-service` 直连 `mall` 库的 `user` + `user_auth` 表（不走 Feign）。JWT 由 `mall-common-security` 用 HMAC-SHA256 签发。密码目前仍为 **MD5**（与种子数据一致；`doc/隐患修复-08-密码MD5存储.md` 建议换 BCrypt，**尚未落地**）。微信 API 调用统一走 `HttpClientUtil` 并透传 `errcode`/`errmsg`。

Endpoints (all under `/auth/**`, whitelisted):

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/login` | 手机号+密码登录 → JWT token 对 |
| `POST` | `/auth/wxLogin` | 微信第一步：code2Session 拿 openid，**永远不签发 token**，统一返回 `needBindPhone=true` + 预填手机号 |
| `POST` | `/auth/bindWechatPhone` | 第二步：`{openid, phone}` 合并/绑定账号 → 签发 JWT |
| `POST` | `/auth/bindWechatPhoneByCode` | `{openid, phoneCode}` 后端调微信解密再复用上面逻辑（需开通「手机号快速验证」，当前未启用） |
| `POST` | `/auth/register` | 账号+手机号注册，写 USERNAME/PHONE 双凭证，自动登录 |
| `POST` | `/auth/refreshToken` | 刷新 token 对 |
| `GET` | `/auth/logout` | access token 写入 Redis 黑名单（`token:blacklist:`） |
| `POST` | `/auth/sms/send` | **mock**（仅打日志） |
| `POST` | `/auth/sms/login` | **未实现**（抛 `短信登录暂未实现`） |

Token rules: access 30min / refresh 7d；所有请求带 `Authorization: Bearer <accessToken>`（缺 `Bearer ` 前缀会 401）；前端 401 时清空 Pinia member store 跳登录页。

## Gateway Routing (`mall-gateway-service/application.yml`)

Routes (无 `/api/` 前缀):

| Path Prefix | Service |
|---|---|
| `/auth/**` | mall-auth-service |
| `/user/**`, `/cart/**`, `/avatar/**` | mall-user-service |
| `/home/**`, `/categories/**`, `/products/**`, `/upload/**`, `/dict/**`, `/health/**` | mall-product-service |
| `/orders/**`, `/pay/**` | mall-order-service |
| `/coupons/**` | mall-marketing-service |
| `/seckill/**` | mall-seckill-service (empty) |
| `/search/**` | mall-search-service |
| `/admin/**` | mall-admin-service |
| `/ai/**` | mall-ai-service |

**`/internal/**` is blocked at the gateway (SetStatus 404)** — Feign calls bypass the gateway and hit service ports directly (`MallConstants.INTERNAL_PREFIX`).

`AuthGlobalFilter` (order=-100) 白名单（免 token）：`/auth/**`, `/home/**`, `/categories/**`, `/products/**`, `/upload/**`, `/dict/**`, `/health/**`, `/avatar/**`, `/user/avatar/upload`, `/pay/wx/notify`, `/admin/login`, `/admin/health`, `/ai/chat`, `/ai/chat/**`, `/ai/health`, `/error`。其余路径须带 token，验签成功后注入 `X-User-Id`/`X-User-Nickname` 头并透传 Authorization。失败返回 `{"code":401,...}`。

> ⚠️ **服务内鉴权缺失**（`doc/隐患修复-04-服务内鉴权缺失.md` 未落地）：`mall-product-service`、`mall-order-service`、`mall-marketing-service`、`mall-search-service`、`mall-ai-service` 未依赖 `mall-common-security`，controller 直接信任网关注入的 `X-User-Id`。绕过网关直连服务端口可伪造用户 ID。只有 user/admin 两个服务自校验 token。

## Architecture & Conventions

### DDD layering (product-service & order-service only)

这两个服务按 DDD 分层，其余服务仍是扁平 `controller/entity/mapper/service`。在 product/order 里开发时遵循：

```
com.hyf.mall<name>service
├── controller/ 或 interfaces/rest/   Controller — protocol translation only
├── service/ 或 application/service/   ApplicationService — orchestrates + @Transactional
├── domain/  (product: entity/event/repository/service; order: repository/impl)
├── dataobject/ + repository/impl + mapper/   infrastructure/persistence
└── api/                               Feign client + DTO for other services
```

Entities/DTOs use Lombok `@Data`. DB entities extend `BaseEntity` (`id`/`createTime`/`updateTime`, maintained by MySQL `DEFAULT CURRENT_TIMESTAMP`). MyBatis: `map-underscore-to-camel-case: true`.

### Shared response/error model (mall-common-core + mall-common-web)

- Every endpoint returns `Result<T>` → `{ code, message, data }`. Use `Result.success(data)` / `Result.error(code, msg)`.
- `ResultCode` code-space: 2xx/4xx/5xx HTTP 语义，1xxx 用户域，2xxx 商品域，3xxx 优惠券域，4xxx 订单域，5xxx 支付域。抛 `new BizException(ResultCode.X)`，`GlobalExceptionHandler` 转成 `Result.error(...)`；`@Valid` 校验错误自动收集。
- Paginated endpoints return `PageResult<T>` → `{ total, page, pageSize, list }`.
- **Constants**: request headers, client types, Redis key templates (`coupon:stock:%d`, `product:hot:rank`, `token:blacklist:`, `REDIS_PREFIX="mall:"`), MQ delay exchange names, `INTERNAL_PREFIX` all live in `MallConstants` — reuse, don't redefine.
- **New shared infra** goes in the relevant `mall-common-*` starter, not a business service.

## Frontends

### `uniapp-shop-vue3-ts`（微信小程序）

uni-app 3.0 + Vue 3 + TS + Pinia (`pinia-plugin-persistedstate`) + Sass。`src/`：`pages/`（首页/分类/购物车/我的/商品/热卖）、`pagesMember/`（设置/资料/地址）、`pagesOrder/`（下单/详情/支付/列表）、`services/`（API 层）、`stores/modules/member`（token/profile 持久化）、`utils/http.ts`、`components/`。

Key patterns:
- baseURL: `import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'`（经网关）
- `http.ts` 拦截器：注入 `Authorization: Bearer <token>` + `source-client: miniapp`，**按本项目 `{code,message,data}` 契约解析（code===200）**，401 自动清理登录态跳登录页，错误统一 toast。
- 登录流程：`wx.login()` → `POST /auth/wxLogin` 拿 openid → 底部 Sheet 输入手机号 → `POST /auth/bindWechatPhone`。`getPhoneNumber` 组件未使用。
- AI 客服入口：「我的」页 → 智能客服 → `src/services/ai.ts` 消费 `/ai/chat` SSE。

### `frontend-admin`（管理后台）

Vue 3 + Vite + TS + Pinia + Naive UI + ECharts。`npm run dev` 默认 5174，Vite 把 `/api/**` 代理到 `http://localhost:8080`（网关）。已对接：admin 登录/profile、sales 看板（overview/products/categories/trend）、商品/SKU/分类 CRUD、**秒杀活动与商品项 CRUD**（`/admin/seckill/**`）、用户状态/等级。

```bash
cd E:/zhiwu-mall/frontend-admin && npm install && npm run dev
cd E:/zhiwu-mall/uniapp-shop-vue3-ts && npm install && npm run dev:mp-weixin
```

## AI Customer Service (`mall-ai-service` + `shopkeeper-agent`)

两层结构：`mall-ai-service`(8087) 是无状态 SSE 代理（WebClient 透传，无 DB/无鉴权），把 `/ai/chat` 的 query 转发给 Python agent；`shopkeeper-agent`(8090, FastAPI + LangGraph) 做 Text-to-SQL 查询 `mall` 库，经 12 节点 StateGraph（Jieba 关键词 → Qdrant/ES 召回 → LLM 过滤/生成 SQL → EXPLAIN 校验 → 执行），SSE 输出 `progress`/`result`/`error` 三态。

- 侧边基础设施（仅在 `shopkeeper-agent/docker` 内）：Qdrant `:6333`、Elasticsearch `:9200`（IK 插件）、TEI embedding `:8089`、`meta` 元数据库。`docker/manage.ps1 up` 一键启动（`build` 模式建知识库，`serve` 模式起服务）。
- 运行：`cd shopkeeper-agent && uv sync && uv run python main.py`（需 LLM API key 配在 `.env`，默认 DashScope `qwen3.6-flash`）。
- LLM/知识库/提示词等细节见 `shopkeeper-agent/README.md` 与 `doc/`，本文件不再展开。

## Docs status: design intent may be ahead of the code

`doc/` 是设计文档，部分描述当前代码尚未实现或已被改动。**动手前以代码为准，文档作为 spec 参考**：

- `doc/秒杀方案分阶段实施计划.md`（当前秒杀工作依据）、`doc/API接口文档.md`（本项目契约，`code/message/data`）是权威。`doc/小程序接口文档.md` 是第三方「小兔鲜儿」契约，**未采用**。
- `doc/隐患修复-01..08` 是问题描述 + 修复方案文档：**01**（凭据出库，已通过 gitignore + dev profile 落地）、**04**（服务内鉴权，**未落地**）、**05/06**（秒杀维度/闭环，已并入秒杀方案）、**07**（延迟交换机参数，已修复）、**08**（MD5→BCrypt，**未落地**）。
- 若实现秒杀，先核对 `doc/基于Redis和MQ实现秒杀订单加购.md` 中的 `productId` 表述已按 SKU 维度统一为 `seckillItemId`。
