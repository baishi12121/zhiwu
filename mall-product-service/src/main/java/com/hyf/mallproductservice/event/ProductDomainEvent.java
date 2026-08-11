package com.hyf.mallproductservice.event;

/**
 * 领域事件标记接口
 *
 * <p>商品域事件：商品上架 / 库存变更 / 热度更新等，
 * 通过 RabbitMQ 发布给 search / marketing 等下游域。
 *
 * @author hyf
 */
public interface ProductDomainEvent {
}
