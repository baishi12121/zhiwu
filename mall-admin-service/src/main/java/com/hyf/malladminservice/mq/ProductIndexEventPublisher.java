package com.hyf.malladminservice.mq;

import com.hyf.mallcommon.core.constant.MallConstants;
import com.hyf.mallcommon.core.event.ProductIndexEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes product index sync events after DB transaction commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndexEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishUpsert(Long productId) {
        publish(ProductIndexEvent.upsert(productId), MallConstants.MQ_PRODUCT_INDEX_UPSERT_ROUTING_KEY);
    }

    public void publishDelete(Long productId) {
        publish(ProductIndexEvent.delete(productId), MallConstants.MQ_PRODUCT_INDEX_DELETE_ROUTING_KEY);
    }

    private void publish(ProductIndexEvent event, String routingKey) {
        if (event == null || event.getProductId() == null) {
            return;
        }
        Runnable task = () -> {
            try {
                rabbitTemplate.convertAndSend(MallConstants.MQ_PRODUCT_INDEX_EXCHANGE, routingKey, event);
                log.info("[product-index] published eventId={}, productId={}, operation={}",
                        event.getEventId(), event.getProductId(), event.getOperation());
            } catch (Exception e) {
                log.error("[product-index] publish failed, eventId={}, productId={}, operation={}, err={}",
                        event.getEventId(), event.getProductId(), event.getOperation(), e.getMessage(), e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
