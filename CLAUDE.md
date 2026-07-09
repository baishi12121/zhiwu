# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`zhiwu-mall` is a Spring Boot 3.5 + Spring Cloud 2025.0.0 + Spring Cloud Alibaba 2023.0.1.2 microservices e-commerce system. Java 17, built with Maven. Five modules under a parent POM (packaging=pom). Each service has its own MySQL database and registers with Nacos.

## Build

```bash
# Build everything
mvn -f E:/zhiwu-mall/pom.xml clean package -DskipTests

# Build a single service
mvn -f E:/zhiwu-mall/mall-product-service/pom.xml clean package -DskipTests

# Run a single service (from its module directory)
mvn -f E:/zhiwu-mall/mall-product-service/pom.xml spring-boot:run
```

No tests exist yet. There is no linting setup.

## Services and Ports

| Service | Port | Database | Notes |
|---|---|---|---|
| `mall-gateway-service` | 8080 | none | Skeleton only — no route config yet. Not a functional gateway. |
| `mall-user-service` | 8081 | `mall_user` | User CRUD, password MD5, role=user |
| `mall-order-service` | 8082 | `mall_order` | Order CRUD, calls product-service via Feign, Seata global tx |
| `mall-coupon-service` | 8083 | `mall_coupon` | Flash-sale coupon grabbing, Sentinel, Redis Lua, RabbitMQ consumer |
| `mall-product-service` | 8084 | `mall_product` | Product CRUD, stock decrease, Redis hot-rank, RabbitMQ consumer |

## Infrastructure (must be running locally)

- **Nacos**: `127.0.0.1:8848` — service discovery + Seata config/registry
- **Sentinel Dashboard**: `localhost:8858` — rate limiting for `POST /coupon/grab`
- **MySQL**: `localhost:3306` — 4 databases, user `root`/`123456`
- **Redis**: `127.0.0.1:6379` — no password, database 1 (used by coupon + product services)
- **RabbitMQ**: `localhost:5672`, vhost `/mall`, user `admin`/`123456` (coupon service) or `guest`/`guest` (order service — inconsistent, see configs)

Initialize databases by running the SQL files in `sql/` (mall_coupon.sql, mall_order.sql, mall_product.sql, mall_user.sql). Each database includes a `tb_*` business table and an `undo_log` table for Seata AT mode.

## Inter-Service Communication

- **Feign**: `mall-order-service` calls `mall-product-service` via `ProductClient` (`POST /products/decrease-stock`). `@EnableFeignClients` is on both order-service and product-service (only order-service uses it currently).
- **RabbitMQ**: Async messaging with Jackson JSON serialization (`Jackson2JsonMessageConverter`).

## Key Technical Patterns

### Seata Distributed Transactions (AT Mode)
All services that touch databases have `seata.enabled: true` with `tx-service-group: mall_tx_group`. `OrderServiceImpl.createOrder()` is annotated `@GlobalTransactional` — it calls `productClient.decreaseStock()` via Feign, then inserts the order locally. Each database has an `undo_log` table for Seata rollback.

### Coupon Seckill Flow (coupon-service)
1. `POST /coupon/grab` (protected by `@SentinelResource`) calls `CouponServiceImpl.seckillCoupon()`
2. Redis Lua script atomically checks stock, checks duplicate user, decrements stock, records user in a Set
3. On success, sends `SeckillMessageDto` to RabbitMQ `COUPON_SECKILL_TOPIC` → queue `coupon.seckill.queue`
4. `CouponMessageListener` consumes messages: DB stock decrement (`UPDATE ... WHERE remain_stock > 0`) + inserts `tb_user_coupon` record (unique index prevents duplicates)
5. `CouponCacheWarmUpTask` preloads active coupons into Redis on startup and every 5 minutes

### Product Hot-Ranking (product-service + order-service)
- Click: `POST /products/click/{id}` → RabbitMQ `product.click.queue` → weight +1
- Order: `OrderServiceImpl.createOrder()` → RabbitMQ `product.order.queue` → weight +5
- `ProductScoreMessageListener`: memory aggregation via `ConcurrentHashMap.merge()` — batches 200 messages or 100ms timer, then flushes to Redis via Pipeline `ZINCRBY` on key `product:hot:rank`
- Query: `GET /products/hot-rank?topN=10` → `ZREVRANGE product:hot:rank 0 N-1`
- Cross-service class mapping: order-service's `ProductScoreMessage` (package `com.hyf.mallorderservice.entity`) is mapped to product-service's version via `DefaultClassMapper.setIdClassMapping()`

### Redis in coupon-service
- `coupon:stock:{id}` — remaining stock counter
- `coupon:users:{id}` — Set of user IDs who successfully grabbed the coupon
- `coupon:info:{id}` — JSON of Coupon entity, TTL 1 hour; bootstrapped by `CouponCacheWarmUpTask` every 5 min

### Stock Decrease (product-service)
`ProductServiceImpl.decreaseStock()` calls `ProductMapper.decreaseStock()` which executes `UPDATE tb_product SET remain_stock = remain_stock - #{count} WHERE id = #{id} AND remain_stock >= #{count}` — prevents overselling at SQL level.

## Package Conventions

Each service follows this structure:
```
com.hyf.mall<name>service
├── MallXxxServiceApplication.java    (always has @SpringBootApplication)
├── common/Result.java                (generic API response wrapper: Result<T>)
├── config/                           (RabbitMQ, Redis configs)
├── controller/                       (REST controllers)
├── entity/                           (DB entity classes, manual getters/setters — no Lombok @Data on entities)
├── dto/                              (only in coupon-service: SeckillMessageDto)
├── mapper/                           (MyBatis mapper interfaces, some use annotations, some use XML)
├── service/                          (interfaces + impl/ sub-package)
├── rabbitmq/                         (MQ producers and @RabbitListener consumers)
└── task/                             (only in coupon-service: scheduled cache warmup)
```

Entity classes use manual getter/setter (not Lombok). DTOs and message classes do use Lombok `@Data`.

## Seata Locked Version

Root POM locks Seata at 2.0.0 for both `seata-spring-boot-starter` and `seata-all`. The comment says this is to avoid an `ArrayIndexOutOfBoundsException` bug in a different version — do not upgrade without verifying the bug is fixed.
