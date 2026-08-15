package com.hyf.mallsearchservice.mq;

import com.hyf.mallcommon.core.constant.MallConstants;
import com.hyf.mallcommon.core.event.ProductIndexEvent;
import com.hyf.mallsearchservice.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes product changes from admin service and keeps ES index in sync.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndexEventConsumer {

    private final SyncService syncService;

    @RabbitListener(queues = MallConstants.MQ_PRODUCT_INDEX_QUEUE)
    public void onMessage(ProductIndexEvent event) {
        if (event == null || event.getProductId() == null || event.getOperation() == null) {
            log.warn("[product-index] ignore invalid event: {}", event);
            return;
        }

        if (ProductIndexEvent.OP_DELETE.equals(event.getOperation())) {
            syncService.deleteProduct(event.getProductId());
            return;
        }
        if (ProductIndexEvent.OP_UPSERT.equals(event.getOperation())) {
            syncService.syncProduct(event.getProductId());
            return;
        }

        log.warn("[product-index] ignore unknown operation, eventId={}, operation={}",
                event.getEventId(), event.getOperation());
    }
}
