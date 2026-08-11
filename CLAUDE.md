# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`zhiwu-mall` is a Spring Boot 3.5.14 + Spring Cloud 2025.0.0 + Spring Cloud Alibaba 2023.0.1.2 microservices e-commerce system. Java 17, Maven multi-module. All services register with Nacos and are fronted by a Spring Cloud Gateway.

Two frontend codebases exist:
- **`uniapp-shop-vue3-ts/`** — uni-app + Vue 3 + TypeScript + Pinia 源码，编译到微信小程序（以及 H5/App 多端）
- **`mp-weixin/`** — 原生微信小程序编译产物（`@dcloudio/uni-mp-weixin` 编译输出），包含 mock 接口层的旧版代码

**Current state = skeleton/DDD refactor in progress.** Most business services expose only a `GET /api/<area>/health` endpoint; controllers, application/domain services, and repository impls are placeholders (return `null`/`0` or empty). **Exceptions: `mall-auth-service` is fully implemented** (see below), and **`mall-ai-service` + `shopkeeper-agent` form a working AI customer-service system** (see AI Customer Service section). When implementing, fill these in — do not assume the target behavior already exists.

## Build & Run

```bash
# Build everything (from repo root)
mvn -f E:/zhiwu-mall/pom.xml clean package -DskipTests

# Build a single service
mvn -f E:/zhiwu-mall/mall-product-service/pom.xml clean package -DskipTests

# Run a single service (must cd into the module dir for spring-boot:run)
cd E:/zhiwu-mall/mall-product-service && mvn spring-boot:run

# AI agent (shopkeeper-agent) — Python/FastAPI
cd E:/zhiwu-mall/shopkeeper-agent
uv sync                              # install dependencies
uv run python main.py                # start on :8090 (needs Qdrant/ES/Embedding running)
# Or via Docker:
cd docker && pwsh manage.ps1 up      # start all 5 services

# Frontend (uniapp-shop-vue3-ts)
cd E:/zhiwu-mall/uniapp-shop-vue3-ts && npm install && npm run dev:mp-weixin
```

No tests exist (`src/test/` is absent) and there is no lint/checkstyle setup. `api_test.py` was removed in this refactor.

### Infrastructure (start before any service)

```bash
docker compose -f E:/zhiwu-mall/docker-compose.yml up -d   # Nacos + Sentinel dashboard
# For AI customer service, also start shopkeeper-agent's infrastructure:
# cd E:/zhiwu-mall/shopkeeper-agent/docker && pwsh manage.ps1 up
```

Then ensure these are running locally:
- **Nacos** `127.0.0.1:8848` — service discovery (also needs 9848/9849 gRPC ports; the compose file maps them)
- **Sentinel Dashboard** `localhost:8858` — only referenced by `mall-marketing-service`
- **MySQL** `localhost:3306`, user `root` / `123456` — **single database `mall`**. Initialize with `sql/init.sql`. Also create `meta` database with `shopkeeper-agent/docker/mysql/meta.sql` for the AI agent
- **Redis** `127.0.0.1:6379`, no password, database `1`
- **RabbitMQ** `localhost:5672`, user `admin` / `123456`, vhost `/mall`

## Module Layout

Root POM (`packaging=pom`) manages Spring Cloud BOMs and the 8 internal `mall-common-*` versions.

`mall-common/` — shared starters every business service composes (parent POM pulls in Lombok globally):
- `mall-common-core` — `Result<T>`, `ResultCode` enum, `BizException`/`UnauthorizedException`, `PageQuery`/`PageResult`, `MallConstants`, `HttpClientUtil` (uses Apache HttpClient, 5s timeout on POST/JSON methods; `doGet` also has timeout configured)
- `mall-common-web` — `GlobalExceptionHandler` (auto-applies via `@RestControllerAdvice`), `CorsConfig`, `LoginUserContext`. Depends on `spring-boot-starter-web` (servlet) — **do not add to the gateway**
- `mall-common-mybatis` — `BaseEntity` (id/createTime/updateTime), MyBatis-Plus auto-config
- `mall-common-redis` — `RedisConfig`: String keys + `GenericJackson2JsonRedisSerializer` (value carries type info)
- `mall-common-rabbitmq` — `RabbitMqConfig`: single `Jackson2JsonMessageConverter` bean for all services
- `mall-common-feign` — `FeignConfig` (RequestInterceptor that copies `Authorization`/`source-client` headers) + `FeignAuthHolder` (ThreadLocal)
- `mall-common-security` — **Fully implemented**: `JwtTokenService` (jjwt HMAC-SHA256 签发/验签), `TokenAuthInterceptor` (解析 `Bearer xxx` 写入 `SecurityContextHolder`, 同时检查 Redis token 黑名单), `JwtStpLogic` (SaToken + JWT 联动), `SaTokenProperties` (白名单/exclude-paths)
- `mall-common-oss` — **Fully implemented**: `OssController` (`POST /upload`, `GET /upload/download-url`), `OssService` (upload + presigned download), `AliOssProperties`. Auto-configured via `@ConditionalOnProperty(alioss.bucket-name)` — services that don't configure OSS won't create beans

Business services (each `@EnableDiscoveryClient` + `@SpringBootApplication`):
| Service | Port | Domain |
|---|---|---|
| `mall-gateway-service` | 8080 | Spring Cloud Gateway (WebFlux) — routes + `AuthGlobalFilter` (JWT 本地验签 + 白名单放行) |
| `mall-user-service` | 8081 | user / address / favorite / footprint / cart — **skeleton**（profile 查询/修改 + 头像上传已实现） |
| `mall-order-service` | 8082 | order aggregate (Order → OrderItem → OrderAddress → Payment) — **skeleton** |
| `mall-marketing-service` | 8083 | coupon / seckill / group-buy / points (only service with Sentinel) |
| `mall-product-service` | 8084 | category / product / SKU / stock — **skeleton** |
| `mall-auth-service` | 8085 | **Fully implemented** — see Authentication section below |
| `mall-search-service` | 8086 | ES search (ES not wired yet) |
| `mall-ai-service` | 8087 | AI customer service — SSE proxy to shopkeeper-agent (Text-to-SQL via LangGraph) |
| `mall-admin-service` | 8088 | admin backend — **skeleton** |

## Authentication (`mall-auth-service`)

`mall-auth-service` directly accesses the `user` + `user_auth` tables in the `mall` database (not via Feign to user-service). JWT tokens use HMAC-SHA256 signed by `mall-common-security`. WeChat API calls (code2Session, access_token, getuserphonenumber) use `HttpClientUtil` — error messages now include WeChat's `errcode` and `errmsg` for debugging.

### Database tables

- **`user`** — `id`, `account`, `nickname`, `password` (MD5), `mobile`, `avatar`, `gender`, `member_level`, `status`, `last_login_at`
- **`user_auth`** — `id`, `user_id`, `identity_type` (USERNAME / PHONE / WECHAT), `identifier`, `credential`

### Endpoints (all under `/auth/**`, whitelisted from token interceptor)

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/login` | **手机号 + 密码登录** — `{phone, password}` → LoginResponse (含 JWT token 对) |
| `POST` | `/auth/wxLogin` | **微信小程序登录（第一步）** — `{code, nickname?, avatar?}` → code2Session 拿 openid。**永远不签发 token**，统一返回 `needBindPhone=true` + `openid` + 已有手机号（老用户预填），需走手机号绑定流程后才能登录 |
| `POST` | `/auth/bindWechatPhone` | **绑定手机号并登录（第二步）** — `{openid, phone}` → 三种场景：①手机号匹配已有用户则合并账号+删除临时用户；②绑定到临时用户；③临时用户已有该手机号则直接绑定。返回完整 LoginResponse（含 JWT token 对） |
| `POST` | `/auth/bindWechatPhoneByCode` | **绑定手机号（phoneCode 解密）** — `{openid, phoneCode}` → 后端调微信 `getuserphonenumber` 解密 phoneCode 为真实手机号，再复用 `bindWechatPhone` 的合并/绑定逻辑。**需要小程序后台开通「手机号快速验证」能力** |
| `POST` | `/auth/register` | **注册** — `{account, password, mobile, nickname}` → 写 user + USERNAME/PHONE 双凭证，自动登录 |
| `POST` | `/auth/refreshToken` | **刷新 token** — `{refreshToken}` → 新 token 对 |
| `GET` | `/auth/logout` | **退出** — 将 access token 写入 Redis 黑名单（key: `token:blacklist:<token>`，TTL 对齐 token 有效期），`TokenAuthInterceptor` 每次校验时检查黑名单 |
| `POST` | `/auth/sms/send` | 发送短信验证码（骨架） |
| `POST` | `/auth/sms/login` | 短信验证码登录（骨架） |

### Login flow

```
密码登录:
  手机号+密码 → 查 user_auth(PHONE) → 验 user.password(MD5) → JWT

微信登录（统一流程，新老用户一致）:
  ① wx.login() code → POST /auth/wxLogin → code2Session 拿 openid
     ├─ 新用户: 自动注册临时用户 → { needBindPhone: true, openid, mobile: null }
     └─ 老用户: { needBindPhone: true, openid, mobile: "138xxxx1234" }
  ② 用户输入手机号 → POST /auth/bindWechatPhone { openid, phone }
     → 合并/绑定账号 → 签发 JWT token 对
  ③ 登录成功 → 跳转首页
```

> **注意**：`/auth/wxLogin` **永远不直接签发 token**。所有微信用户（无论新旧）都必须走手机号验证流程。老用户的已有手机号会预填在输入框中，点"允许"即可。

### Token requirements (frontend)

- Every authenticated request MUST carry `Authorization: Bearer <accessToken>` — **missing `Bearer ` prefix will cause 401**
- Access token TTL: 30 min; Refresh token TTL: 7 days
- On 401 response (code 401 or 1003), clear Pinia member store and redirect to `/pages/login/login`

## AI Customer Service System (`mall-ai-service` + `shopkeeper-agent`)

The AI customer service is a two-tier system: a Java microservice (`mall-ai-service`) acts as a transparent SSE proxy, forwarding user questions to a Python LangGraph agent (`shopkeeper-agent`) that performs Text-to-SQL querying against the `mall` database.

### Architecture flow

```
[Uniapp Frontend]
    | POST /ai/chat  (SSE, text/event-stream)
    v
[mall-ai-service :8087]  (Spring Boot — stateless SSE proxy)
    | POST http://localhost:8090/api/query  (SSE)
    v
[shopkeeper-agent :8090]  (FastAPI + LangGraph — Text-to-SQL agent)
    | SQL queries
    v
[MySQL mall database]  (the same `mall` DB used by all business services)
```

Side infrastructure used only by shopkeeper-agent:
- **Qdrant** `:6333` — vector database, 2 collections (`column_info_collection`, `metric_info_collection`), 1024-dim Cosine similarity
- **Elasticsearch** `:9200` — full-text index (`value_index`) with IK Chinese analyzer for column value search
- **TEI (Text Embeddings Inference)** `:8089` — serves `BAAI/bge-large-zh-v1.5` for 1024-dim embeddings
- **Meta MySQL database** `meta` — structured metadata (tables, columns, metrics, column-metric relationships), separate from the `mall` business DB

These are started via `docker compose` in the shopkeeper-agent directory, separate from the main project's `docker-compose.yml`.

### `mall-ai-service` — Java SSE proxy (port 8087)

A thin stateless proxy layer. No database, no authentication, no AI logic — purely forwards SSE streams.

**Dependencies**: `mall-common-web` (for `Result<T>`, CORS, exception handling), `spring-boot-starter-webflux` (for `WebClient`), Nacos discovery, Spring Cloud LoadBalancer. Does NOT depend on MyBatis, Redis, RabbitMQ, OSS, or Security.

**Package structure** (`com.hyf.mallaiservice`):

| Class | Role |
|---|---|
| `MallAiServiceApplication` | `@SpringBootApplication` + `@EnableDiscoveryClient` |
| `config/AiServiceConfig` | Creates `WebClient` bean (`aiAgentWebClient`) pointing at shopkeeper-agent's base URL, with configurable timeout |
| `controller/AiController` | REST controller — 3 endpoints (see below) |
| `service/AiAgentService` | Core proxy: `chat(query)` returns `Flux<String>` by POSTing to shopkeeper-agent's `/api/query`, error → graceful SSE error message |
| `properties/AiAgentProperties` | `@ConfigurationProperties(prefix="mall.ai.agent")` — `baseUrl`, `queryPath`, `timeoutMs` |
| `dto/ChatRequest` | Inbound: `query` (String, `@NotBlank`, `@Size(max=500)`) |
| `dto/AgentQueryRequest` | Outbound: `query` (String), mirrors shopkeeper-agent's `QuerySchema` |

**Endpoints** (all under `/ai/**`, whitelisted at the gateway — no auth required):

| Method | Path | Description |
|---|---|---|
| `GET` | `/ai/health` | Health check + shopkeeper-agent reachability test (probes `/docs`) |
| `POST` | `/ai/chat` | **Main streaming endpoint** — returns `text/event-stream`. Forwards query to shopkeeper-agent, passes SSE events through unmodified |
| `GET` | `/ai/chat/test` | Non-streaming test endpoint — `.blockLast()` on the full response |

**Configuration** (`application.yml`):
- `server.port: 8087`
- `spring.mvc.async.request-timeout: 300000` (5 min — critical for long SSE streams; without this Tomcat kills the connection)
- `mall.ai.agent.base-url: http://localhost:8090`
- `mall.ai.agent.query-path: /api/query`
- `mall.ai.agent.timeout-ms: 60000`

### `shopkeeper-agent` — Python Text-to-SQL agent (port 8090)

A production-grade intelligent data query agent. **Tech stack**: Python 3.14, FastAPI + uvicorn, LangGraph 1.1+, LangChain (OpenAI-compatible LLM), SQLAlchemy 2.0 + asyncmy (async MySQL), Qdrant (vector search), Elasticsearch (full-text search), HuggingFace TEI (embeddings), Jieba (Chinese NLP), Loguru (logging).

**Package manager**: `uv` (`pyproject.toml` + `uv.lock`). **Linting**: Ruff + pre-commit.

#### Project structure

```
shopkeeper-agent/
├── main.py                    # FastAPI app (port 8090), CORS, request_id middleware
├── conf/
│   ├── app_config.yaml        # MySQL/Qdrant/ES/Embedding/LLM/logging config
│   └── meta_config.yaml       # Knowledge base: 9 tables, 30+ columns, 4 metrics to index
├── app/
│   ├── agent/                 # LangGraph workflow
│   │   ├── graph.py           # 12-node StateGraph definition
│   │   ├── state.py           # DataAgentState (TypedDict — shared state across nodes)
│   │   ├── context.py         # DataAgentContext (TypedDict — runtime dependencies, not in state)
│   │   ├── llm.py             # LLM singleton (OpenAI-compatible, temperature=0)
│   │   └── nodes/             # 12 node implementations
│   ├── api/                   # FastAPI router, schemas, dependencies, lifespan
│   ├── clients/               # Connection managers: MySQL, Qdrant, ES, Embedding
│   ├── conf/                  # Python config loaders (OmegaConf + dataclasses)
│   ├── core/                  # Loguru logging + request_id ContextVar
│   ├── entities/              # Dataclasses: ColumnInfo, TableInfo, MetricInfo, ValueInfo
│   ├── models/                # SQLAlchemy ORM models (meta DB: table_info, column_info, etc.)
│   ├── prompt/                # Prompt file loader
│   ├── repositories/          # Data access: Meta MySQL, DW MySQL, Qdrant, Elasticsearch
│   ├── scripts/               # build_meta_knowledge.py — CLI for knowledge base indexing
│   └── services/              # QueryService (orchestrates one query) + MetaKnowledgeService
├── prompts/                   # 7 .prompt template files (Chinese)
├── docker/                    # docker-compose, Dockerfile, entrypoint.sh, meta.sql, ES IK plugin
└── docs/                      # Architecture diagrams
```

#### LangGraph pipeline (12 nodes, ~8 LLM calls per query)

```
START
  → extract_keywords          (Jieba TF-IDF + POS filtering — no LLM)
  → recoll_column             (LLM expands keywords → embed → Qdrant vector search)
  → recoll_value              (LLM expands keywords → ES full-text search with IK analyzer)
  → recoll_metric             (LLM expands keywords → embed → Qdrant vector search)
    [3 parallel branches]
  → merge_retrieved_info      (7 sub-steps: deduplicate, fill metric columns, merge values,
                               organize by table, fill PK/FK, build TableInfo/MetricInfo state)
  → filter_table              (LLM selects needed tables+columns from candidates)
  → filter_metric             (LLM selects needed metrics from candidates)
    [2 parallel branches]
  → add_extra_context         (today's date/day-of-week/quarter + DB dialect/version)
  → generate_sql              (LLM generates SQL from YAML-structured context)
  → validate_sql              (EXPLAIN against real DB — no LLM)
  → conditional edge:
      error=None? → run_sql   (execute and stream results)
      error≠None? → correct_sql (LLM fixes SQL) → run_sql
```

Every node emits `{"type": "progress", "step": "...", "status": "running/success/error"}` via SSE so the frontend sees real-time pipeline progress.

#### Knowledge base (`conf/meta_config.yaml`)

Defines what the agent knows about the database:

- **9 tables** across 3 domains:
  - Product: `product`, `product_sku`, `spec`, `spec_value`, `sku_spec_value`, `category`, `brand`
  - Marketing: `coupon`
  - Order: `order`, `order_item`
- **4 metrics**: 商品价格 (product price), 商品库存 (inventory), 商品销量 (sales count), 订单实付金额 (order paid amount)
- Each column specifies: `name`, `role` (primary_key/foreign_key/measure/dimension), `description`, `alias` list, `sync` flag (whether to index values in ES)

#### API endpoint

| Method | Path | Request | Response |
|---|---|---|---|
| `POST` | `/api/query` | `{"query": "统计华北地区销售总额"}` | `text/event-stream` SSE with 3 message types: `progress`, `result`, `error` |

SSE message format:
```json
{"type": "progress", "step": "抽取关键词", "status": "success"}
{"type": "result", "data": [{"销售总额": 123456.78}]}
{"type": "error", "message": "SQL syntax error..."}
```

#### Database connections (all to host machine's MySQL)

| Database | Host | Purpose |
|---|---|---|
| `meta` | `host.docker.internal:3306` | Structured metadata: table_info, column_info, metric_info, column_metric |
| `mall` | `host.docker.internal:3306` | The real zhiwu-mall data warehouse for query execution |

Both share the same MySQL instance as the main project. The `meta` database must be initialized with `docker/mysql/meta.sql` before first use.

#### Docker deployment

5 services in `docker/docker-compose.yaml`:
- `elasticsearch` (custom build with IK plugin, `:9200`)
- `kibana` (`:5601`, for ES exploration)
- `qdrant` (`:6333`, vector DB)
- `embedding` (TEI, `:8089` — port offset to avoid conflict with mall-user-service `:8081`)
- `shopkeeper-agent` (built from `docker/Dockerfile`, `:8090`)

MySQL is NOT containerized — the agent connects to the host machine's MySQL via `host.docker.internal`.

**Entrypoint modes** (PowerShell: `docker/manage.ps1`):
- `serve` — start FastAPI server (waits for Qdrant/ES/Embedding to be ready first)
- `build` — run `build_meta_knowledge.py` to index the knowledge base, then exit

#### Knowledge base initialization workflow

Before the agent can answer questions, the knowledge base must be built:
1. `MetaKnowledgeService` reads `meta_config.yaml`
2. Queries the `mall` database for real column types (`SHOW COLUMNS`) and example values (`SELECT DISTINCT`)
3. Persists metadata to Meta MySQL (`table_info`, `column_info`, `metric_info`, `column_metric`)
4. Embeds column names/descriptions/aliases → upserts to Qdrant `column_info_collection`
5. Embeds metric names/descriptions/aliases → upserts to Qdrant `metric_info_collection`
6. Indexes column values for `sync: true` columns → bulk indexes to Elasticsearch `value_index`

#### Prompt templates (7 `.prompt` files)

| File | Used by | Output format |
|---|---|---|
| `extend_keywords_for_column_recall.prompt` | recall_column | JSON array |
| `extend_keywords_for_metric_recall.prompt` | recall_metric | JSON array |
| `extend_keywords_for_value_recall.prompt` | recall_value | JSON array |
| `filter_table_info.prompt` | filter_table | JSON object |
| `filter_metric_info.prompt` | filter_metric | JSON array |
| `generate_sql.prompt` | generate_sql | Plain SQL (10 rules: SELECT only, backticks, LIMIT 20, no markdown, etc.) |
| `correct_sql.prompt` | correct_sql | Plain SQL (minimum changes, preserve semantics) |

#### LLM configuration

- **Model**: `qwen3.6-flash` (via DashScope, OpenAI-compatible API)
- **Base URL**: `https://dashscope.aliyuncs.com/compatible-mode/v1`
- **Temperature**: 0 (deterministic SQL generation)
- **API key**: Set via `LLM_API_KEY` in `.env`
- Can be swapped to any OpenAI-compatible provider by changing `LLM_BASE_URL` and `LLM_MODEL_NAME`

## Frontend (`uniapp-shop-vue3-ts`)

### Tech stack
uni-app 3.0 + Vue 3.2 + TypeScript 5.1 + Pinia 2.0 (with `pinia-plugin-persistedstate`) + Sass. Compiles to mp-weixin / H5 / App.

### Directory structure
```
src/
├── pages/          业务页面（login, index, category, cart, my, goods, hot）
├── pagesMember/    会员分包（settings, profile, address）
├── pagesOrder/     订单分包（create, detail, payment, list）
├── services/       API 层（login, home, goods, cart, order, pay, profile, address, ai）
├── stores/         Pinia stores（modules/member — 用户 token/profile 持久化）
├── types/          TS 类型定义（member.d.ts, goods.d.ts, order.d.ts 等）
├── utils/          http.ts（请求拦截器 + baseURL）
├── components/     公共组件（XtxSwiper, XtxGuess, vk-data-goods-sku-popup）
└── static/         图片/tabbar 图标
```

### Key patterns
- **API base URL**: `import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'`（经网关）
- **Token persistence**: Pinia `member` store → `persist: { storage: { getItem/setItem: uni.getStorageSync/setStorageSync } }`
- **HTTP interceptor**: 自动拼接 baseURL、注入 `Authorization: Bearer <token>`、注入 `source-client: miniapp` header、401 自动清理登录态并跳转。错误消息由 http.ts 统一 toast，页面 catch 块只做 console.error 日志记录，不重复 toast。
- **LoginResult type** (`src/types/member.d.ts`): `{userId, nickname, avatar, memberLevel, accessToken, refreshToken, expiresIn, needBindPhone?, openid?, mobile?}`
- **AI chat**: `src/services/ai.ts` consumes the `/ai/chat` SSE endpoint. Entry point: "我的" page → 智能客服 button navigates to the chat page (`src/pages/my/my.vue` line ~79)

### Login page behavior (`src/pages/login/login.vue`)

微信登录当前使用**手机号输入绑定**流程（不依赖微信 `getPhoneNumber` 组件）：

1. 点击"微信一键登录" → `wx.login()` 拿 code → `POST /auth/wxLogin` 拿 openid
2. 弹出底部 Sheet："绑定手机号完成登录"
   - 老用户：输入框预填已有手机号
   - 新用户：需手动输入
3. 点"允许" → `POST /auth/bindWechatPhone { openid, phone }` → 登录成功

> `getPhoneNumber` 组件和 `/auth/bindWechatPhoneByCode` 端点已实现但**当前未使用**，如需启用需先去微信公众平台开通「手机号快速验证」能力。

## Architecture

### DDD layering (product-service & order-service only)
These two are restructured into DDD packages; the other services are still flat (`controller/entity/mapper/service`). When working in product/order, follow this layering:
```
com.hyf.mall<name>service
├── interfaces/rest/      Controller — protocol translation only
├── application/service/  ApplicationService — orchestrates domain logic + @Transactional
├── domain/
│   ├── model/aggregate/  Aggregate root (holds invariants, e.g. OrderAggregate)
│   ├── model/entity/     Entities extend BaseEntity
│   ├── model/valueobject/
│   ├── repository/        Repository interface (impl lives in infrastructure)
│   ├── service/          DomainService — cross-entity logic
│   └── event/            Domain events
├── infrastructure/persistence/  RepositoryImpl, MyBatis mappers, DO↔domain conversion
└── api/                  Feign client + DTO package for other services to call
```
Entities hold no behavior; behavior lives on the aggregate root.

### Gateway routing (`mall-gateway-service/application.yml`)
Routes map path prefixes to `lb://<service-name>` (note: **no `/api/` prefix** in actual routes):

| Path Prefix | Service |
|---|---|
| `/auth/**` | mall-auth-service |
| `/user/**`, `/cart/**`, `/avatar/**` | mall-user-service |
| `/home/**`, `/categories/**`, `/products/**`, `/upload/**`, `/dict/**`, `/health/**` | mall-product-service |
| `/orders/**` | mall-order-service |
| `/coupons/**` | mall-marketing-service |
| `/search/**` | mall-search-service |
| `/admin/**` | mall-admin-service |
| `/ai/**` | mall-ai-service |

**`/internal/**` is blocked at the gateway (returns 404)** — Feign calls bypass the gateway and hit service ports directly.

### Gateway Auth Filter (`config/AuthGlobalFilter.java`)

Global filter (order=-100) that validates JWT tokens at the gateway level using local jjwt verification:

- **Whitelist** (no token required): `/auth/**`, `/home/**`, `/categories/**`, `/products/**`, `/upload/**`, `/dict/**`, `/health/**`, `/avatar/**`, `/user/avatar/upload`, `/error`
- **Non-whitelisted paths**: require `Authorization: Bearer <token>`, JWT parsed and verified locally (same secret as services)
- On success: injects `X-User-Id` and `X-User-Nickname` headers + forwards original `Authorization` to downstream
- On failure: returns `{"code":401,"message":"<reason>","data":null}` (JSON, HTTP 401)
- Explicit failure messages: "缺少访问令牌" / "访问令牌已过期" / "访问令牌无效"

### Inter-service contracts
The intended Feign call is order→product `POST /internal/products/decrease-stock` (declared in `ProductApiPackage` and the API doc), but it is **not yet implemented**. `FeignConfig` transparently forwards `Authorization`/`source-client` headers on every Feign call.

### Shared response/error model (mall-common-core + mall-common-web)
- Every endpoint returns `Result<T>` → `{ code, message, data }`. Use `Result.success(data)` / `Result.error(code, msg)`.
- Business codes: HTTP-style 200/400/401/403/404/429/500 plus domain codes 1001–1003 (user), 2001–2002 (product), 3001–3003 (coupon), 4001 (order) — defined in `ResultCode`. Throw `new BizException(ResultCode.X)` and `GlobalExceptionHandler` converts it to `Result.error(...)`. Validation errors (`@Valid`) are auto-collected.
- Paginated endpoints return `PageResult<T>` → `{ total, page, pageSize, list }`.

## Important: docs describe a design that is ahead of the code

`doc/` contains design intent, not current behavior. Treat it as a spec to implement against, but **verify against the code before trusting specifics** — the refactor changed several decisions the docs still assume:
- **Single database `mall`**, not 4 per-service DBs (the old `mall_user/mall_product/mall_order/mall_coupon` were merged). Initialize with `sql/init.sql` (NOT `sql/mall.sql`). `undo_log`/Seata were removed.
- **No Seata** — the `@GlobalTransactional` distributed-transaction flow described in the hot-rank and API docs has been dropped. Stock decrease for orders is intended to be a local MyBatis `UPDATE ... WHERE remain_stock >= ?` (not yet coded).
- **No MQ code in src** — the click/order hot-ranking, coupon-seckill async consumers, and `ProductScoreMessageListener` exist only in `doc/商品热度排行榜设计文档.md`. `mall-common-rabbitmq` only provides the JSON converter bean.
- `doc/小程序接口文档.md` is a third-party (Apifox "小兔鲜儿") contract the project has **not** adopted — it documents a different response shape (`msg`/`result`). The project's own contract is `doc/API接口文档.md` (`code`/`message`/`data`).
- **Frontend-backend gap**: the uni-app frontend was built for a different API. Only `/auth/*`, `/user/profile`, and `/ai/chat` endpoints work end-to-end. Frontend calls to `/home/**`, `/cart/**`, `/orders/**`, `/pay/**`, `/member/**`, `/categories/top` have **no backend endpoint** yet and will fail. Gateway routes for `/cart/**` and `/avatar/**` go to `mall-user-service`, but the corresponding controllers don't exist.

## Conventions to follow when adding code

- **Lombok**: entities and DTOs use `@Data`; the old rule of "no Lombok on entities" no longer applies (see `Product`, `OrderAggregate`).
- **DB entities** extend `BaseEntity` (get `id`/`createTime`/`updateTime`); `createTime`/`updateTime` are maintained by MySQL `DEFAULT CURRENT_TIMESTAMP [ON UPDATE]`.
- **MyBatis**: `mapper-locations: classpath:mapper/*.xml`, `map-underscore-to-camel-case: true`. Product/order services alias DOs under `infrastructure.persistence.dataobject`; user/marketing under `entity`.
- **Constants**: Redis keys (`coupon:stock:%d`, `product:hot:rank`, `token:blacklist:`, `wechat:access_token`) and header names live in `MallConstants` — reuse rather than redefining.
- **New common infra** should go in the relevant `mall-common-*` starter so all services inherit it (e.g. a token interceptor belongs in `mall-common-security`, not a business service).
- **Error handling in WeChat API calls**: always log the full raw response and include `errcode` + `errmsg` in the thrown exception message so the error is traceable from both backend logs and frontend toast messages.
- **`HttpClientUtil.doGet`** now has timeout config and reads error response bodies (previously swallowed non-200 responses). Both `doPost` and `doPost4Json` have null-safe `response.close()` in finally blocks.
