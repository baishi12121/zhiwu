package com.hyf.mallorderservice.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class ProductScoreMessage implements Serializable {
    private Long productId;
    private String actionType;
    private Long timestamp;
}
