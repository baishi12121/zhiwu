-- Phase 2 秒杀可靠性增强增量 DDL。

CREATE TABLE IF NOT EXISTS `seckill_stock_compensate` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `message_id`      VARCHAR(128)    NOT NULL COMMENT '关联业务messageId；取消类使用orderNo',
  `activity_id`     BIGINT UNSIGNED NOT NULL,
  `seckill_item_id` BIGINT UNSIGNED NOT NULL,
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `quantity`        INT             NOT NULL,
  `compensate_type` TINYINT         NOT NULL COMMENT '1下单失败 2支付超时 3用户取消 4对账偏差',
  `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '0待处理 1已完成 2失败',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id_type` (`message_id`, `compensate_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀库存补偿流水';
