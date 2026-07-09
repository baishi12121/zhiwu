# zhiwu-mall 项目架构文档

> 本文档记录 `zhiwu-mall` 项目的完整架构信息，作为后续回答问题的检索基准。  
> 任何对本项目架构、模块边界、技术选型、调用链、数据模型的提问都应先检索本文档。

---

## 1. 项目总览

`zhiwu-mall` 是一个基于 **Spring Boot 3.5 + Spring Cloud 2025.0.0 + Spring Cloud Alibaba 2023.0.1.2** 的微服务电商系统，使用 **Java 17 + Maven** 构建，**uniapp + Vue3 + TS + Vite5** 作为跨端前端。

### 1.1 顶层目录结构

```
zhiwu-mall/
├── pom.xml                       # 父 POM（packaging=pom，统一管理 5 个子模块）
├── docker-compose.yml            # Nacos + Sentinel Dashboard 容器编排
├── .gitignore
├── CLAUDE.md                     # Claude Code 上下文（与本文档互为补充）
├── sql/                          # 4 个业务库的初始化脚本
│   ├── mall_coupon.sql
│   ├── mall_order.sql
│   ├── mall_product.sql
│   └── mall_user.sql
├── doc/
│   └── 商品热度排行榜设计文档.md  # 已存在的热榜设计文档
├── mall-gateway-service/         # 端口 8080，网关（仅骨架）
├── mall-user-service/            # 端口 8081
├── mall-order-service/           # 端口 8082
├── mall-coupon-service/          # 端口 8083
├── mall-product-service/         # 端口 8084
├── mall-uniapp/                  # 前端工程（uniapp 多端）
└── mp-weixin/                    # 微信小程序构建产物（dist 目录）
```

### 1.2 服务端口 / 数据库 / 关键依赖

| 服务 | 端口 | 数据库 | 关键能力 | 是否真接入 |
|---|---|---|---|---|
| `mall-gateway-service` | 8080 | 无 | 网关骨架（未配路由，未启用 gateway 依赖） | ❌ 骨架 |
| `mall-user-service` | 8081 | `mall_user` | 用户 CRUD，MD5 密码 | ✅ |
| `mall-order-service` | 8082 | `mall_order` | 订单 CRUD，Feign 调商品，Seata 全局事务 | ✅ |
| `mall-coupon-service` | 8083 | `mall_coupon` | 优惠券秒杀，Sentinel 限流，Redis Lua，MQ 消费 | ✅ |
| `mall-product-service` | 8084 | `mall_product` | 商品 CRUD，扣减库存，Redis 热榜，MQ 消费 | ✅ |

> ⚠️ `mall-gateway-service` 当前只是 `@SpringBootApplication` 空壳，没有引入 `spring-cloud-starter-gateway`，也没有路由配置，不是一个可用的网关。

### 1.3 前端子工程

- **mall-uniapp**：unibest 模板（`uniapp + Vue3 + TS + Vite5 + UnoCSS + uview-plus + z-paging`）
- 支持平台：H5、微信小程序、App、支付宝小程序、字节小程序等
- 包管理：**pnpm**
- HTTP 客户端：**alova**（已封装） + 备用 `uni.request` 包装
- 状态管理：**Pinia** + `pinia-plugin-persistedstate`
- **当前状态**：大部分页面使用 `src/mock/mall.ts` 模拟数据，**未对接后端**；`login.ts` 调的是 `/auth/login`、`/user/info` 等尚未实现的后端接口。

---

## 2. 后端公共约定

### 2.1 父 POM 锁定版本

[pom.xml](file:///e:/zhiwu-mall/pom.xml)

- Spring Boot：`3.5.14`
- Spring Cloud：`2025.0.0`
- Spring Cloud Alibaba：`2023.0.1.2`
- **Seata 强制锁版本 `2.0.0`**（`seata-spring-boot-starter` + `seata-all`）—— 注释明确说明为规避某版本的 `ArrayIndexOutOfBoundsException` Bug，不要升级
- 统一 groupId：`com.hyf` / artifactId：`zhiwu-mall` / version：`0.0.1-SNAPSHOT`
- 模块列表（5 个）：`mall-coupon-service`、`mall-gateway-service`、`mall-order-service`、`mall-product-service`、`mall-user-service`

### 2.2 包结构约定

每个微服务都遵循：

```
com.hyf.mall<name>service
├── MallXxxServiceApplication.java   # @SpringBootApplication
├── common/Result.java               # 统一返回包装
├── config/                          # RabbitMQ / Redis / Seata 等配置
├── controller/                      # REST 控制器
├── entity/                          # DB 实体（Lombok @Data + @NoArgsConstructor + @AllArgsConstructor，保留 Serializable）
├── mapper/                          # MyBatis Mapper
├── service/                         # 接口 + impl/ 实现
├── rabbitmq/                        # MQ 生产者 / @RabbitListener 消费者
├── task/                            # 定时任务（仅 coupon-service）
└── api/                             # OpenFeign 客户端接口（仅 order-service 有 ProductClient）
```

**重要编码风格**：
- 实体类用**手写** getter/setter + 全参/无参构造
- DTO / 消息体用 **Lombok `@Data`**
- Mapper 风格混用：注解（`@Update`/`@Insert`）和 XML（`OrderMapper.xml`、`ProductMapper.xml`、`UserMapper.xml`）都有
- 公共返回 `Result<T>`：`{ code, message, data }`，成功 `200`，业务异常 `500`（按 controller 自定义其他业务码如 `400/404/429`）

### 2.3 基础设施依赖

| 组件 | 地址 | 用途 |
|---|---|---|
| Nacos | `127.0.0.1:8848` | 服务注册 / 配置中心 / Seata registry&config |
| Sentinel Dashboard | `localhost:8858` | `mall-coupon-service` 限流监控 |
| MySQL | `localhost:3306` | root/123456，4 个库 |
| Redis | `127.0.0.1:6379` | db 1，**无密码**，被 coupon + product 共享 |
| RabbitMQ | `localhost:5672` | vhost `/mall`，**注意 user 不一致** |
| Seata Server | （需独立部署） | 事务协调器，配置存于 Nacos |

**docker-compose 当前只编排 Nacos + Sentinel**，MySQL / Redis / RabbitMQ / Seata Server 需本地独立安装。

### 2.4 跨服务通信矩阵

| 通信方式 | 链路 | 说明 |
|---|---|---|
| **OpenFeign** | `mall-order-service` → `mall-product-service` `POST /products/decrease-stock` | `@FeignClient(name="mall-product-service")` + `@EnableFeignClients` |
| **RabbitMQ** | 多链路异步 | `Jackson2JsonMessageConverter` JSON 序列化 |
| **Seata AT** | `mall-order-service` `@GlobalTransactional` 包裹「Feign 扣库存 + 插订单」 | 事务组 `mall_tx_group` |
| **LoadBalancer** | 所有注册到 Nacos 的服务都依赖 | ribbon → spring-cloud-loadbalancer |

### 2.5 RabbitMQ 凭据不一致（已记录问题）

- `mall-coupon-service/application.yml`：`admin/123456` vhost `/mall`
- `mall-order-service/application.yml`：`guest/guest` （**无 vhost**）
- **实际生产中必须统一**（CLAUDE.md 已标注此问题）

---

## 3. 数据库 Schema

### 3.1 `mall_user` — 用户库

[tb_user](file:///e:/zhiwu-mall/sql/mall_user.sql#L21-L36)
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK AUTO_INCREMENT | 用户 ID |
| username | varchar(50) UNIQUE | 用户名 |
| password | varchar(100) | MD5 密文（32 位） |
| phone | varchar(20) UNIQUE | 手机号 |
| balance | decimal(10,2) | 模拟下单余额 |
| status | tinyint | 0 禁用 / 1 正常 |
| create_time / update_time | datetime | |

种子数据：3 个用户（admin/zhangsan/lisi），密码统一为 `123456` 的 MD5 `e10adc3949ba59abbe56e057f20f883e`。

### 3.2 `mall_product` — 商品库

[tb_product](file:///e:/zhiwu-mall/sql/mall_product.sql#L21-L34)
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | 商品 ID |
| name | varchar(150) | |
| price | decimal(10,2) | |
| total_stock | int | 总库存 |
| remain_stock | int | **剩余库存（防超卖字段）** |
| status | tinyint | 0 下架 / 1 上架 |

种子数据：iPhone 17 Pro / 无线降噪蓝牙耳机 / 人体工学电脑椅。

### 3.3 `mall_order` — 订单库

[tb_order](file:///e:/zhiwu-mall/sql/mall_order.sql#L21-L35)
| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | 订单号 |
| user_id | bigint | |
| coupon_id | bigint NULL | 使用的优惠券 |
| total_amount | decimal(10,2) | 订单总金额 |
| real_amount | decimal(10,2) | 实付金额 |
| status | tinyint | 0 未支付 / 1 已支付 / 2 已取消 |
| product_id | int | 关联商品 ID |

### 3.4 `mall_coupon` — 优惠券库

[tb_coupon](file:///e:/zhiwu-mall/sql/mall_coupon.sql#L21-L33) | [tb_user_coupon](file:///e:/zhiwu-mall/sql/mall_coupon.sql#L45-L56)
- `tb_coupon`：id / title / total_stock / remain_stock / status(0 失效 1 正常)
- `tb_user_coupon`：id / user_id / coupon_id / status(0 未用 1 已用 2 过期) / create_time / use_time
- **唯一索引 `uni_user_coupon(user_id, coupon_id)`** —— 防止同一用户重复抢同一券

### 3.5 Seata `undo_log` 表

四个库都内置了标准 Seata AT 模式 `undo_log` 表（`xid` + `branch_id` 唯一索引），用于回滚日志。

---

## 4. 微服务详解

### 4.1 `mall-user-service` (8081)

**定位**：基础用户服务，**目前未被任何其他服务调用**，仅暴露 REST API。

**接口**（[`UserController`](file:///e:/zhiwu-mall/mall-user-service/src/main/java/com/hyf/malluserservice/controller/UserController.java)）：

| Method | Path | 用途 |
|---|---|---|
| POST | `/users` | 注册（密码 MD5） |
| DELETE | `/users/{id}` | 删除 |
| PUT | `/users` | 更新（密码若非 32 位则 MD5） |
| GET | `/users/{id}` | 详情 |
| GET | `/users/username/{username}` | 按名查（密码字段返回 `******`） |
| GET | `/users` | 全部列表（密码字段返回 `******`） |

**关键实现**：
- [`UserServiceImpl.md5Encrypt`](file:///e:/zhiwu-mall/mall-user-service/src/main/java/com/hyf/malluserservice/service/impl/UserServiceImpl.java#L153-L169)：手写 `MessageDigest.getInstance("MD5")` 16 进制小写
- 注册时 `selectByUsername` 排重
- 所有写操作 `@Transactional(rollbackFor = Exception.class)`
- Seata 同样启用（`tx-service-group: mall_tx_group`），但**目前没有任何分布式事务入口**
- 启用了 `@EnableFeignClients` 但**未定义任何 `@FeignClient`**

### 4.2 `mall-product-service` (8084)

**定位**：商品中心 + 商品热度排行榜（核心复杂业务）。

**接口**（[`ProductController`](file:///e:/zhiwu-mall/mall-product-service/src/main/java/com/hyf/mallproductservice/controller/ProductController.java)）：

| Method | Path | 用途 |
|---|---|---|
| POST | `/products` | 新增商品（默认 `remain_stock=total_stock`，`status=1`） |
| DELETE | `/products/{id}` | 删除 |
| PUT | `/products` | 动态更新非空字段；若同时改 `total_stock` 而未给 `remain_stock`，自动按差值调整 |
| GET | `/products/{id}` | 详情 |
| GET | `/products` | 全部（按 `create_time DESC`） |
| POST | `/products/decrease-stock?id=&count=` | **Feign 入口**：`UPDATE tb_product SET remain_stock=remain_stock-#{count} WHERE id=#{id} AND remain_stock>=#{count}` 防超卖 |
| POST | `/products/click/{id}` | 发送 CLICK 热度消息 |
| GET | `/products/hot-rank?topN=10` | 从 Redis ZSET `product:hot:rank` 取 topN（最大 100） |

**热榜架构**（与 `doc/商品热度排行榜设计文档.md` 完全一致）：

- 交换机：`exchange.product.rank`（Topic，durable）
- 队列：`product.click.queue`（路由键 `routing.product.click`） / `product.order.queue`（路由键 `routing.product.order`）
- 消息体 `ProductScoreMessage`（productId / actionType "CLICK"|"ORDER" / timestamp）
- 跨服务类映射：[`RabbitConfig#messageConverter`](file:///e:/zhiwu-mall/mall-product-service/src/main/java/com/hyf/mallproductservice/config/RabbitConfig.java#L79-L99) 通过 `DefaultClassMapper.setIdClassMapping()` 把 `com.hyf.mallorderservice.entity.ProductScoreMessage` 映射到本地类
- 消费聚合：[`ProductScoreMessageListener`](file:///e:/zhiwu-mall/mall-product-service/src/main/java/com/hyf/mallproductservice/rabbitmq/ProductScoreMessageListener.java)：
  - `ConcurrentHashMap<String, Double> scoreBuffer` 按 productId 累加
  - `AtomicInteger messageCount` 满 200 立即刷写
  - `ScheduledExecutorService` 每 100ms 兜底刷写
  - 刷写：持锁做纳秒级 `swap + clear`，锁外用 Redis Pipeline (`executePipelined`) 执行 `ZINCRBY`
  - 权重：CLICK +1.0，ORDER +5.0
  - `@PreDestroy` 优雅停机最后一次刷写
- 查询：`ZREVRANGE product:hot:rank 0 N-1 WITHSCORES`，异常时返回空列表（降级）

**Redis 使用**：
- 仅 db 1
- `StringRedisTemplate`
- 没有配置连接池参数（依赖默认 Lettuce）

### 4.3 `mall-order-service` (8082)

**定位**：订单中心，**Seata 分布式事务入口**。

**接口**（[`OrderController`](file:///e:/zhiwu-mall/mall-order-service/src/main/java/com/hyf/mallorderservice/controller/OrderController.java)）：

| Method | Path | 用途 |
|---|---|---|
| POST | `/orders` | **创建订单（核心事务入口）** |
| DELETE | `/orders/{id}` | 删除 |
| PUT | `/orders/{id}` | 动态更新 |
| GET | `/orders/{id}` | 详情 |
| GET | `/orders` | 全部 |
| GET | `/orders/user/{userId}` | 用户订单 |
| GET | `/orders/demo` | 联通测试 |

**核心事务流**（[`OrderServiceImpl.createOrder`](file:///e:/zhiwu-mall/mall-order-service/src/main/java/com/hyf/mallorderservice/service/impl/OrderServiceImpl.java#L44-L72)）：

```
1. 校验：order / userId / totalAmount / realAmount 非空
2. Feign 调 mall-product-service: POST /products/decrease-stock（id, 1）
3. 设 status=0（未支付）
4. orderMapper.insert(order)
5. 发 ORDER 热度消息到 exchange.product.rank/routing.product.order
6. 返回 Order
```

- 整段被 `@GlobalTransactional(rollbackFor = Exception.class)` 包裹
- 若 Feign 失败 → Seata 回滚「商品库存 + 订单插入」
- MQ 消息在事务提交**前**发送（**潜在缺陷**：事务回滚后消息已发出，热榜会多算 — 设计文档已标注为「发件即忘，最终一致」）

**RabbitMQ 配置**（[`RabbitConfig`](file:///e:/zhiwu-mall/mall-order-service/src/main/java/com/hyf/mallorderservice/config/RabbitConfig.java)）：只声明 `Jackson2JsonMessageConverter`，**没有声明交换机/队列**（依赖商品服务去声明或 broker 自动创建）。

### 4.4 `mall-coupon-service` (8083)

**定位**：高并发秒杀场景，整合 Redis Lua + Sentinel + MQ + 唯一索引。

**接口**（[`CouponController`](file:///e:/zhiwu-mall/mall-coupon-service/src/main/java/com/hyf/mallcouponservice/controller/CouponController.java)）：

| Method | Path | 用途 |
|---|---|---|
| POST | `/coupon/grab?couponId=&UserId=...` | **秒杀**（`UserId` 走 `@RequestHeader`） |

`@SentinelResource(value = "/coupon/grab", blockHandler = "handleGrabBlock")` — 流控降级时返回 429。

**秒杀执行流**（[`CouponServiceImpl.seckillCoupon`](file:///e:/zhiwu-mall/mall-coupon-service/src/main/java/com/hyf/mallcouponservice/service/impl/CouponServiceImpl.java#L27-L62)）：

```
1. KEYS: stockKey = "coupon:stock:{id}", userSetKey = "coupon:users:{id}"
2. 执行 Lua 脚本（原子）：
   - stock = GET stockKey
   - 0: 库存为空/<=0       → return 0
   - SISMEMBER users userId == 1 → return 1（重复抢）
   - DECR stockKey; SADD users userId
   - return 2（成功）
3. 分支：
   0 → "已抢完"
   1 → "已抢过"
   2 → 发消息到 COUPON_SECKILL_TOPIC 异步落库，返回"成功"
```

**Redis 关键 key**：
- `coupon:stock:{id}` — 库存计数器
- `coupon:users:{id}` — Set 记录已抢用户
- `coupon:info:{id}` — JSON 优惠券详情，TTL 1h
- `coupon:info:{id}` 与 `coupon:stock:{id}` 由 [`CouponCacheWarmUpTask`](file:///e:/zhiwu-mall/mall-coupon-service/src/main/java/com/hyf/mallcouponservice/task/CouponCacheWarmUpTask.java) 在 `@PostConstruct` 和每 5 分钟 `@Scheduled(fixedDelay=300_000)` 写入
- 写入策略：`info` 直接覆盖；`stock` 用 `setIfAbsent` 避免覆盖秒杀进行中的实时值

**RabbitMQ 拓扑**（[`RabbitMqConfig`](file:///e:/zhiwu-mall/mall-coupon-service/src/main/java/com/hyf/mallcouponservice/config/RabbitMqConfig.java)）：
- Exchange：`COUPON_SECKILL_TOPIC`（Topic，durable）
- Queue：`coupon.seckill.queue`（durable）
- Routing Key：`coupon.seckill.routing.key`
- 发送：先调 `rabbitTemplate.convertAndSend(topic, "", message)`（**注：空 routingKey 会导致绑定到 topic 失效！** — 实际发送端和绑定端 key 不匹配，需注意 — 现行代码使用 `rabbitTemplate.convertAndSend(topic, "", message)` 空字符串 routing key）

**消费者**（[`CouponMessageListener.onMessage`](file:///e:/zhiwu-mall/mall-coupon-service/src/main/java/com/hyf/mallcouponservice/rabbitmq/CouponMessageListener.java#L29-L59)）：
- `@Transactional(rollbackFor = Exception.class)` 包住整段
- 步骤：`UPDATE tb_coupon SET remain_stock=remain_stock-1 WHERE id=? AND remain_stock>0`  →  `INSERT INTO tb_user_coupon`
- `DuplicateKeyException` 捕获（MQ 重投导致唯一索引冲突时直接放行）
- 抛异常时依赖 `acknowledge-mode: auto` + 监听器配置 retry 3 次

**优惠券核销**（[`CouponUseServiceImpl.useCoupon`](file:///e:/zhiwu-mall/mall-coupon-service/src/main/java/com/hyf/mallcouponservice/service/impl/CouponUseServiceImpl.java)）：`UPDATE tb_user_coupon SET status=1, use_time=NOW() WHERE user_id=? AND coupon_id=? AND status=0`，被 `@GlobalTransactional(name="seata-use-coupon")` 包裹 — **目前无任何调用方**。

**RabbitMQ 压测优化**（application.yml）：
- `concurrency: 10`，`max-concurrency: 50`
- `prefetch-count: 50`
- 重试 3 次，初始间隔 1s，倍数 2

**Sentinel 限流**：Dashboard localhost:8858，端口 8719 客户端上报（默认未配规则，需在 Dashboard 上为 `/coupon/grab` 配置 QPS 阈值）。

### 4.5 `mall-gateway-service` (8080)

**当前状态**：
- 只引入 `spring-boot-starter`（**未引入 gateway 依赖**）
- `@SpringBootApplication` 空壳
- 注册到 Nacos（`mall-gateway-service` 名字）
- 没有路由、没有 Filter、没有 GlobalCors — **不是可工作的网关**

---

## 5. 关键设计模式与代码约束

### 5.1 Seata 分布式事务（AT 模式）

- 事务组：`mall_tx_group`
- 配置 + 注册中心：Nacos（`group: SEATA_GROUP`）
- 入口：`OrderServiceImpl.createOrder`（`@GlobalTransactional`） + `CouponUseServiceImpl.useCoupon`（`@GlobalTransactional`）
- 每个业务库都建有 `undo_log` 表
- 锁定版本 `2.0.0`（避坑） — 不要升

### 5.2 库存防超卖

**两层防御**：
1. **SQL 兜底**：`UPDATE ... WHERE remain_stock >= #{count}` —— 库存不够就直接影响 0 行
2. **乐观锁 / 唯一索引**：扣减 `tb_coupon.remain_stock` 同样使用 `> 0` 条件；`tb_user_coupon` 依赖 `uni_user_coupon` 唯一索引

### 5.3 Redis Lua 秒杀原子性

[`seckillScript`](file:///e:/zhiwu-mall/mall-coupon-service/src/main/java/com/hyf/mallcouponservice/config/RedisConfig.java#L14-L33)：
- 单脚本完成「查库存 → 查重复 → 扣库存 → 记用户」四步
- 返回码：0 库存空 / 1 重复 / 2 成功
- 配合 `setIfAbsent` 缓存预热避免覆盖实时库存

### 5.4 RabbitMQ 消息序列化

- 统一 `Jackson2JsonMessageConverter`
- 商品服务通过 `DefaultClassMapper.setIdClassMapping()` 解决跨服务同名类包路径不同的反序列化问题
- 订单服务只声明 converter，不声明拓扑（依赖 broker 自动创建 + 商品服务声明）

### 5.5 消息聚合 + Pipeline 刷写

- 目的：避免热榜 200 QPS 把 Redis 写穿
- 模式：内存 `ConcurrentHashMap.merge` 累加 + 200/100ms 双触发 + 锁外 Pipeline `ZINCRBY`
- 详见 `doc/商品热度排行榜设计文档.md`

---

## 6. 前端 mall-uniapp

### 6.1 技术栈

- 框架：`uniapp` 3.0 + Vue 3.4 + TS 5.8 + Vite 5.2
- 路由：**约定式路由**（`pages.config.ts` + `src/pages/**`）
- 状态：Pinia + 持久化
- 样式：UnoCSS（原子化） + uview-plus UI 库 + z-paging 分页
- HTTP：alova（默认）+ 备用 `http.ts`（基于 `uni.request`）
- 工具：dayjs / vue-i18n / vue-router

### 6.2 路由（[pages.config.ts](file:///e:/zhiwu-mall/mall-uniapp/pages.config.ts)）

底部 tabBar：首页 / 购物车 / 分类 / 我的（`src/tabbar/config.ts` 配置，支持 native/custom 两种策略）

非 tabBar 页面：`order/checkout`、`order/list`、`product/detail`、`product/list`、`me/coupon`、`me/favorite`、`me/footprint`、`me/address`、`me/service-chat`、`me/help`、`me/about-version`、`me/settings`、`me/edit-profile`、`me/messages`、`about/about`

### 6.3 HTTP 封装

- [`http.ts`](file:///e:/zhiwu-mall/mall-uniapp/src/http/http.ts)：基于 `uni.request`，支持 token 失效自动刷新（双 token 模式）
- [`alova.ts`](file:///e:/zhiwi-mall/mall-uniapp/src/http/alova.ts)：alova 实例，支持动态域名 `VITE_SERVER_BASEURL`
- **当前 `VITE_SERVER_BASEURL` 注释未配置**（`# VITE_SERVER_BASEURL = 'https://dev.xxx.com'`）

### 6.4 API 接口层（[`src/api/`](file:///e:/zhiwu-mall/mall-uniapp/src/api)）

| 文件 | 接口 |
|---|---|
| `login.ts` | `getCode`、`login`、`refreshToken`、`getUserInfo`、`logout`、`updateInfo`、`updateUserPassword`、`wxLogin` — **后端均未实现** |
| `me.ts` | `getMeProfile` / `getMeOrderStats` / `getMeProfileServices` — **全部走 mock 模拟 200ms 延迟** |
| `foo.ts` / `foo-alova.ts` | demo 接口 |

### 6.5 Mock 数据（[`src/mock/mall.ts`](file:///e:/zhiwu-mall/mall-uniapp/src/mock/mall.ts)）

- 模拟首页 banner / 分类 / 商品（连衣裙、T 恤、牛仔裤、耳机、收纳箱等）
- 订单状态枚举：`unpaid / unshipped / unreceived / unreviewed / refund`
- 用户资料兜底

### 6.6 状态管理（Pinia）

- `useTokenStore`（[token.ts](file:///e:/zhiwu-mall/mall-uniapp/src/store/token.ts)）：双 token 模式（access + refresh），持久化
- `useUserStore`（[user.ts](file:///e:/zhiwu-mall/mall-uniapp/src/store/user.ts)）：用户信息
- 全部 `persist: true` → 写入本地 storage

### 6.7 前端 vs 后端的对接状态

| 前端期望接口 | 后端是否实现 |
|---|---|
| `POST /auth/login` | ❌ |
| `GET /user/info` | ❌（`mall-user-service` 只有 `/users/...`） |
| `POST /auth/refreshToken` | ❌ |
| `GET /auth/logout` | ❌ |
| `GET /me/profile` `GET /me/orderStats` `GET /me/services` | ❌（用 mock） |
| `GET /products` | ✅ `mall-product-service` |
| `POST /products/click/{id}` | ✅ |
| `GET /products/hot-rank` | ✅ |
| `POST /coupon/grab` | ✅ |

**结论**：前端目前是一个**完全独立于后端**的 demo 状态，没有真实联调。

---

## 7. 核心调用链路总图

```
┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ mall-user    │   │ mall-product │   │ mall-order   │   │ mall-coupon  │
│   :8081      │   │   :8084      │   │   :8082      │   │   :8083      │
└──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘
       │                  │                  │                  │
       │                  │   Feign 调       │                  │
       │                  │◀─────────────────│                  │
       │                  │  /products/      │                  │
       │                  │  decrease-stock  │                  │
       │                  │                  │                  │
       │                  │  RabbitMQ 消费   │                  │
       │                  │◀─────────────────┼─ ORDER 消息       │
       │                  │  exchange.product.rank               │
       │                  │                  │                  │
       │                  │                  │            Lua 原子操作
       │                  │                  │            Redis SET/GET
       │                  │                  │                  │
       │                  │                  │   Seata AT 全局事务
       │                  │◀─────────────────│   扣库存+插订单    │
       │                  │                  │                  │
       │                  │            Sentinel 流控             │
       │                  │                  │            POST /coupon/grab
       │                  │                  │                  │
       │                  │   Redis ZSET product:hot:rank        │
       │                  │   ←──ZINCRBY──                     │
       │                  │                  │                  │
       │                  │   GET /products/hot-rank            │
       │                  │   → ZREVRANGE                      │
       │                  │                  │                  │
       │   共享 db:        │   共享 db:        │   共享 db:        │   共享 db:
│ mall_user    │   │ mall_product │   │ mall_order   │   │ mall_coupon  │
│ +undo_log    │   │ +undo_log    │   │ +undo_log    │   │ +undo_log    │
└──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘

所有服务 ──→ Nacos 127.0.0.1:8848（注册 + Seata 配置/注册中心）
mall-coupon-service ──→ Sentinel Dashboard localhost:8858
```

---

## 8. 构建与运行

### 8.1 后端构建

```bash
# 整体打包
mvn -f E:/zhiwu-mall/pom.xml clean package -DskipTests

# 单服务
mvn -f E:/zhiwu-mall/mall-product-service/pom.xml clean package -DskipTests

# 单服务运行
mvn -f E:/zhiwu-mall/mall-product-service/pom.xml spring-boot:run
```

启动顺序（建议）：Nacos → Redis → RabbitMQ → MySQL → 业务服务（任意顺序）。Seata Server 需独立部署并配置 Nacos 注册。

### 8.2 前端

```bash
cd E:/zhiwu-mall/mall-uniapp
pnpm install
pnpm dev          # H5 (默认端口 9000)
pnpm dev:mp       # 微信小程序（输出到 dist/dev/mp-weixin）
pnpm build:h5     # H5 生产构建
```

构建产物 `mp-weixin/` 目录是已经存在的一份历史产物（**注意：实际是上一版构建输出**，与 `src/` 当前内容可能不一致）。

### 8.3 docker-compose

```bash
cd E:/zhiwu-mall && docker-compose up -d
```
只启动 Nacos(8848/9848/9849) + Sentinel Dashboard(8858)。其他基础设施仍需本地安装。

---

## 9. 已知问题与改进点

| 编号 | 问题 | 位置 | 建议 |
|---|---|---|---|
| 1 | `mall-gateway-service` 是空壳，没 gateway 依赖 | [pom.xml](file:///e:/zhiwu-mall/mall-gateway-service/pom.xml) | 加 `spring-cloud-starter-gateway` + 路由 |
| 2 | RabbitMQ 凭据不一致 | [coupon yml](file:///e:/zhiwu-mall/mall-coupon-service/src/main/resources/application.yml#L17-L20) vs [order yml](file:///e:/zhiwu-mall/mall-order-service/src/main/resources/application.yml#L13-L15) | 统一为 admin/123456 vhost /mall |
| 3 | `coupon.seckill.routing.key` 绑定但 `convertAndSend` 用空字符串 | [RabbitMqConfig#bindingSeckill](file:///e:/zhiwu-mall/mall-coupon-service/src/main/java/com/hyf/mallcouponservice/config/RabbitMqConfig.java#L32-L37) vs [RabbitMqProducer#send](file:///e:/zhiwu-mall/mall-coupon-service/src/main/java/com/hyf/mallcouponservice/rabbitmq/RabbitMqProducer.java#L18) | 发送端改为 `convertAndSend(topic, "coupon.seckill.routing.key", message)` |
| 4 | ORDER 消息在 Seata 事务**前**发出，回滚后热榜会误增 | [OrderServiceImpl#createOrder](file:///e:/zhiwu-mall/mall-order-service/src/main/java/com/hyf/mallorderservice/service/impl/OrderServiceImpl.java#L62-L69) | 注册 Seata 钩子在 commit 之后发送，或用本地消息表 |
| 5 | 前端未对接后端 | `mall-uniapp/src/api/login.ts` 等 | 需在网关打通后逐个联调 |
| 6 | `CouponUseServiceImpl` 无任何调用方 | 同上 | 由未来订单结算流程调用 |
| 7 | `mall-user-service` 启用了 FeignClients 但无客户端 | [application](file:///e:/zhiwu-mall/mall-user-service/src/main/java/com/hyf/malluserservice/MallUserServiceApplication.java#L10) | 移除无用注解或定义真实客户端 |
| 8 | 订单服务未声明交换机/队列，靠 broker 自动创建 | [RabbitConfig](file:///e:/zhiwu-mall/mall-order-service/src/main/java/com/hyf/mallorderservice/config/RabbitConfig.java) | 显式声明 exchange/queue/binding |
| 9 | Sentinel Dashboard 端口 `8719` 被注释但 yml 启用 | [coupon yml](file:///e:/zhiwu-mall/mall-coupon-service/src/main/resources/application.yml#L14) | 与 docker-compose 中 `8719` 注释保持一致 |
| 10 | 仓库根有 `.pepsicode/`、`session_*.jsonl` 调试残留 | 根目录 | 移到 `.gitignore` |

---

## 10. 后续检索提示

后续任何问题按以下顺序检索本文档：
- 端口/数据库/中间件 → §1.2、§2.3
- 表结构 → §3
- 某个微服务职责 → §4
- 跨服务调用链 → §5 + §7
- 前端技术细节 → §6
- 已知 bug → §9
- **新需求开发、添加表/接口** → §11「目标架构」优先
- **接口字段定义** → [`doc/API接口文档.md`](file:///e:/zhiwu-mall/doc/API接口文档.md)
- **新库建表 SQL** → [`sql/mall.sql`](file:///e:/zhiwu-mall/sql/mall.sql)

---

## 11. 目标架构（统一库 + 完整 API 体系）

> 本节是**重构目标**，与 §1~§9 描述的「当前状态」并存。  
> 所有新功能开发、问题排查优先参考本节。  
> 重构决策（已与用户确认）：**5 个微服务保留、4 库合并为 `mall`、移除 Seata**。

### 11.1 统一库 `mall`

**位置**：[`sql/mall.sql`](file:///e:/zhiwu-mall/sql/mall.sql)

**16 张表，按业务域聚合**：

| 域 | 表 | 关键字段 | 前端对应 |
|---|---|---|---|
| 用户 | `user` | username/password/phone/avatar/gender/balance/member_level/growth/status | 我的页、登录 |
| 用户 | `user_auth` | user_id + identity_type(USERNAME/PHONE/WECHAT) + identifier + credential | 多端登录 |
| 用户 | `user_address` | receiver/province/city/district/detail_address/is_default | 地址管理 |
| 用户 | `user_favorite` | user_id + product_id (UK) | 我的收藏 |
| 用户 | `user_footprint` | user_id + product_id + viewed_at | 浏览足迹 |
| 用户 | `user_cart` | user_id + product_id + sku_id + quantity + checked | 购物车 |
| 用户 | `user_coupon` | user_id + coupon_id (UK) + status + use_time + order_id | 我的优惠券 |
| 商品 | `category` | parent_id（树）+ name + icon + accent_color + highlight | 分类 tabBar |
| 商品 | `product` | category_id + price + original_price + total_stock + remain_stock + sales + tags + is_flash_sale + cover_colors | 首页/列表/详情 |
| 商品 | `product_image` | product_id + image_url + sort_order | 详情轮播 |
| 商品 | `product_sku` | product_id + spec_text + color_label/hex + price + stock | 规格选择 |
| 营销 | `coupon` | title + coupon_type + threshold/discount + total/remain_stock + valid_start/end | 券列表/秒杀 |
| 营销 | `banner` | title + subtitle + cta + background + link_type/value | 首页轮播 |
| 订单 | `order` | order_no(UK) + user_id + status + total/real_amount + discount/freight + coupon_id + address_id + address_snapshot(JSON) | 我的订单 |
| 订单 | `order_item` | order_id + product_id + sku_id + name/image/spec_text 快照 + price + quantity + subtotal | 订单详情 |
| 订单 | `order_status_log` | order_id + from_status + to_status + operator | 状态流转 |
| 系统 | `dict` | dict_type + dict_key + dict_label + extra(JSON) | 枚举 |

**已删除**：
- 4 个 `undo_log` 表（Seata 移除）
- 4 个独立库（mall_user / mall_product / mall_order / mall_coupon）

**索引约定**：
- 所有表主键 `BIGINT UNSIGNED AUTO_INCREMENT`
- 时间字段 `DATETIME DEFAULT CURRENT_TIMESTAMP [ON UPDATE]`
- 唯一索引显式 `uk_*`，普通索引 `idx_*`
- 金额用 `DECIMAL(10,2)`，禁止 `float/double`
- 字符集 `utf8mb4_unicode_ci`

### 11.2 完整 API 体系

**位置**：[`doc/API接口文档.md`](file:///e:/zhiwu-mall/doc/API接口文档.md)（13 大节，~60 个接口）

按业务域归类：

| 大节 | 服务 | 接口数 | 重点 |
|---|---|---|---|
| 2 Auth | user | 7 | 密码/短信/微信三登录，双 token |
| 3 Me | user | 6 | 资料/订单统计 |
| 4 Address | user | 5 | 默认地址联动 |
| 5 Favorite/Footprint | user + product | 5 | 收藏唯一索引 |
| 6 Home | product | 4 | banners/quickCategories/flashSale/recommend |
| 7 Category | product | 2 | 树形 |
| 8 Product | product | 6 | 分页搜索/详情/SKU/热榜/扣库存(internal) |
| 9 Cart | user | 6 | 多 SKU 合并 |
| 10 Preview | order | 1 | 价格计算 |
| 11 Order | order | 7 | 创建/列表/详情/支付/取消/收货/评价 |
| 12 Coupon | coupon | 4 | 可领/我的/抢券/核销 |
| 13 Common | product | 3 | dict/upload/health |

### 11.3 关键架构变化（相对当前实现）

| 项 | 当前 | 目标 | 影响 |
|---|---|---|---|
| 数据库 | 4 个独立库 | `mall` 单库 | 5 个 yml 改 datasource.url 相同 |
| Seata | 全部启用 | 全部移除 | pom 删依赖，yml 删配置，代码删 `@GlobalTransactional` |
| 订单-商品事务 | Seata 包裹 Feign 调 product | 同一库内本地 `@Transactional` | 删 `ProductClient` Feign，改用 `ProductMapper` 直调 |
| 扣库存接口 | `POST /products/decrease-stock` 暴露公网 | 移入 `/internal/products/decrease-stock`，由 Feign 调 | 路径变 |
| 库存防超卖 | SQL `WHERE remain_stock >= ?` | 保留不动 | 不变 |
| 抢券 | Redis Lua + MQ 异步落库 | 保留不动 | 不变 |
| 热榜 | Redis ZSET + 消息聚合 | 保留不动 | 不变 |
| Nacos | 注册 + Seata 配置 | 只保留注册 | yml 删 Seata config 块 |
| RabbitMQ | 多链路 | 保留 | 凭据统一为 `admin/123456` vhost `/mall` |
| 订单 ORDER 消息 | 事务前发出，回滚有副作用 | 事务提交后发出（Spring `TransactionSynchronizationManager`） | order-service 改 |

### 11.4 字段命名约束（强约束）

- 表名：小写 `snake_case`，**无前缀**（不再用 `tb_`），按业务域前缀（`user_` / `product_` / `order_` / `banner_` / `dict`）
- 字段名：小写 `snake_case`
- 主键：所有表统一 `id BIGINT UNSIGNED`
- 业务编号：`xxx_no` 命名（如 `order_no`）
- 时间字段：`create_time` / `update_time` / `xxx_time`（`xxx_at` 不用）
- 状态字段：通用名 `status`（TINYINT，配合 `dict` 表维护语义）
- JSON 字段：`_json` 或 `_snapshot` 后缀
- 删除：物理删除 + 软删字段 `is_deleted`（二期再做）

### 11.5 待办清单（重构代码阶段）

1. ✅ 写 [sql/mall.sql](file:///e:/zhiwu-mall/sql/mall.sql)（**已完成**）
2. ✅ 写 [doc/API接口文档.md](file:///e:/zhiwu-mall/doc/API接口文档.md)（**已完成**）
3. ⏳ 删除原 4 个 SQL 文件（mall_user.sql / mall_product.sql / mall_order.sql / mall_coupon.sql）
4. ⏳ 改 5 个服务的 `application.yml`：`datasource` 全部指向 `jdbc:mysql://localhost:3306/mall`，删 Seata 块
5. ⏳ 删 5 个 pom.xml 里的 Seata 依赖 + Redis/RabbitMQ 不需要改
6. ⏳ order-service：删 `ProductClient`，改 `OrderServiceImpl.createOrder` 走本地 mapper；加 `TransactionSynchronizationManager` 注册 commit 后钩子
7. ⏳ coupon-service：删 `@GlobalTransactional`；发送消息改用真实 routing key `coupon.seckill.routing.key`
8. ⏳ 全部 controller 按 API 文档重写路径（`/api/...`），删除旧 `/users` `/products` `/orders` `/coupon` 路径
9. ⏳ 新增 entity / mapper / service：user_address、user_favorite、user_footprint、user_cart、category、product_image、product_sku、banner、order、order_item、order_status_log、dict
10. ⏳ 抽出公共 `mall-common` 模块（Result / 异常 / 分页 / Feign 配置），消除 5 个 service 重复
11. ⏳ 网关：`mall-gateway-service` 引入 `spring-cloud-starter-gateway`，写路由表

### 11.6 不动的部分

- ✅ Redis 键设计（`coupon:stock:{id}` / `coupon:users:{id}` / `coupon:info:{id}` / `product:hot:rank`）
- ✅ RabbitMQ 拓扑（`exchange.product.rank` / `product.click.queue` / `product.order.queue` / `COUPON_SECKILL_TOPIC`）
- ✅ Sentinel 限流点（`/api/coupons/{id}/grab`）
- ✅ 父子 POM 版本锁定
- ✅ Seata 版本锁 `2.0.0` 注释（保留作为历史）
