package com.hyf.mallproductservice.entity;


import lombok.Data;

import java.io.Serializable;

/**
 * MQ消息体
 */
@Data
public class ProductScoreMessage implements Serializable {
    private Long productId;
    private String actionType; // "CLICK" 或 "ORDER"
    private Long timestamp;
}
