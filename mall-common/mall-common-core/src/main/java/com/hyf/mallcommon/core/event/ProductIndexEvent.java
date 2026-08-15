package com.hyf.mallcommon.core.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Product search index sync event.
 */
public class ProductIndexEvent {

    public static final String OP_UPSERT = "UPSERT";
    public static final String OP_DELETE = "DELETE";

    private String eventId;
    private Long productId;
    private String operation;
    private LocalDateTime occurredAt;

    public ProductIndexEvent() {
    }

    public ProductIndexEvent(String eventId, Long productId, String operation, LocalDateTime occurredAt) {
        this.eventId = eventId;
        this.productId = productId;
        this.operation = operation;
        this.occurredAt = occurredAt;
    }

    public static ProductIndexEvent upsert(Long productId) {
        return of(productId, OP_UPSERT);
    }

    public static ProductIndexEvent delete(Long productId) {
        return of(productId, OP_DELETE);
    }

    private static ProductIndexEvent of(Long productId, String operation) {
        return new ProductIndexEvent(UUID.randomUUID().toString(), productId, operation, LocalDateTime.now());
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
