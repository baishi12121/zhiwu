# 任务 06：order-service 协作改造

> 目标：让 order-service 在**用户主动取消秒杀订单**时，Feign 调用 seckill-service 的 `/internal/seckill/orders/{orderNo}/cancel` 回补库存。这是唯一跨服务改动（支付已在 order-service 处理，共用 order 表，无需改动）。

## 前置
- 任务 05 完成（seckill-service 已暴露 `/internal/seckill/orders/{orderNo}/cancel`）
- 参考：`mall-order-service` 现有取消逻辑、`mall-common-feign`（`FeignConfig` 自动拷贝 `Authorization`/`source-client` 头）

## 规格（改 `mall-order-service`）

### 1. `mall-order-service/pom.xml` 追加依赖
```xml
<dependency>
    <groupId>com.hyf</groupId>
    <artifactId>mall-common-feign</artifactId>
</dependency>
```
> 若已依赖则跳过。`FeignConfig` 会自动透传 `Authorization`/`source-client` 头，保证内部调用带用户身份。

### 2. api/ 下新增 Feign 客户端
```java
// 包：com.hyf.mallorderservice.api（或该服务已有 api 包）
@FeignClient(name = "mall-seckill-service", path = "/internal/seckill")
public interface SeckillCancelFeignClient {
    @PostMapping("/orders/{orderNo}/cancel")
    Result<Void> cancelSeckillOrder(@PathVariable("orderNo") String orderNo,
                                    @RequestBody SeckillCancelDTO dto);
}
```
- `SeckillCancelDTO`：`activityId`/`seckillItemId`/`userId`/`quantity`
- `Result` 用 `mall-common-core` 的 `Result<T>`；非 200 code 抛异常或返回 false（按现有调用风格）
- 记得在主类或配置上 `@EnableFeignClients(basePackages = "com.hyf.mallorderservice.api")`（若已有扫描则跳过）

### 3. 在用户取消订单的路径里加回补钩子
- 找到用户取消入口（如 `OrderApplicationService.cancelOrderByUser`/controller 对应方法）
- 取消成功后判断：**该订单 `order_source == 2` 且 `order_state == 1 待付款`** → 调 `SeckillCancelFeignClient.cancelSeckillOrder(orderNo, dto)`
  - dto 的字段从 order + order_item 组装（activityId/seckillItemId/userId/quantity）
- Feign 失败的处理：记 ERROR 日志（可重试）；不要阻塞主流程取消——由 seckill-service 的兜底扫描（任务 05 第 5 条）最终补上库存
- 注意：**普通订单（order_source=1）不触发**，避免多余调用

## 硬性约束
- 只加"回补通知"，不改 order-service 的订单主流程/状态机。
- Feign 走 `mall-common-feign`（头透传）；网关不参与（`/internal/**` 已被网关拦外部访问，Feign 直连服务端口）。

## 完成标准 / 自测
- `mvn -f E:/zhiwu-mall/mall-order-service/pom.xml clean compile -DskipTests` 通过。
- 联调：建一笔秒杀订单 → 用户在 order-service 取消 → seckill-service 收到内部调用 → 库存（Redis+DB）回补；`seckill-item` 库存与订单取消状态一致。
- 普通订单取消 → seckill-service 无任何调用日志。
- 汇报：Feign 调用日志、回补前后库存、普通订单无副作用。
