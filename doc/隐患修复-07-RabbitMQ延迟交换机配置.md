# RabbitMQ 延迟交换机配置修复文档

## 问题是什么

订单超时取消依赖 RabbitMQ 的 `rabbitmq_delayed_message_exchange` 插件。配置类中创建了参数：

```java
args.put("x-delayed-type", "direct");
```

但构造 `CustomExchange` 时没有把 `args` 传进去，导致声明的交换机缺少 `x-delayed-type` 参数。

## 影响

- Java 编译不会报错。
- 服务启动连接 RabbitMQ 并声明交换机时可能失败。
- 订单超时取消消息无法正常延迟投递。
- 未支付订单可能不会自动取消，库存和优惠券长期占用。

## 怎么修复

### 1. 正确传入交换机参数

修改 `RabbitMQDelayedConfig`：

```java
@Bean
public CustomExchange orderDelayExchange() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-delayed-type", "direct");
    return new CustomExchange(
            MallConstants.MQ_ORDER_DELAY_EXCHANGE,
            "x-delayed-message",
            true,
            false,
            args);
}
```

### 2. 启动前检查插件

RabbitMQ 必须安装并启用插件：

```bash
rabbitmq-plugins enable rabbitmq_delayed_message_exchange
```

如果使用 Docker，需要确认镜像已包含该插件，或者在构建镜像时安装。

### 3. 增加启动健康检查

服务启动后验证交换机存在且类型正确：

```java
rabbitAdmin.getRabbitTemplate().execute(channel -> {
    channel.exchangeDeclarePassive(MallConstants.MQ_ORDER_DELAY_EXCHANGE);
    return null;
});
```

如果被动声明失败，启动失败或健康检查标记 DOWN。

### 4. 确认消息持久化

发送延迟消息时设置 delivery mode：

```java
message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
message.getMessageProperties().setHeader(MallConstants.MQ_X_DELAY_HEADER, delayMillis);
```

### 5. 提供降级方案

如果部署环境不能安装延迟插件，可以改用：

- TTL 队列 + 死信交换机。
- 定时任务扫描 `pay_latest_time < now` 的待支付订单。

建议至少保留定时任务兜底，避免 MQ 插件异常导致订单永不取消。

## 验证方式

1. 未安装延迟插件时，服务启动或健康检查应明确失败。
2. 安装插件后，交换机声明成功。
3. 创建订单后，等待配置的超时时间，订单自动取消。
4. 自动取消后，库存和优惠券被释放。
