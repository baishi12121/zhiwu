-- =====================================================================
--  秒杀订单扩展 DDL（针对已有数据库的增量脚本，非 init.sql 全量）
--  适用场景：数据库已有数据，只需追加秒杀相关字段与表
-- =====================================================================

USE `mall`;

-- ---------- 1. 扩展 order 表：新增秒杀属性字段 ----------
ALTER TABLE `order`
  ADD COLUMN `order_source`    TINYINT         NOT NULL DEFAULT 1 COMMENT '订单来源 1普通 2秒杀',
  ADD COLUMN `activity_id`     BIGINT UNSIGNED DEFAULT NULL       COMMENT '秒杀活动ID（order_source=2 时必填）',
  ADD COLUMN `seckill_item_id` BIGINT UNSIGNED DEFAULT NULL       COMMENT '秒杀商品项ID，关联 seckill_item.id';

-- ---------- 2. 扩展 order 表：新增秒杀幂等唯一索引 ----------
-- 语义：同一用户在同一秒杀活动中购买同一秒杀商品项，最多一笔订单
-- 注意：MySQL 唯一索引允许多个 NULL 共存，普通订单（activity_id/seckill_item_id 为 NULL）不受约束
ALTER TABLE `order`
  ADD UNIQUE KEY `uk_user_activity_item` (`user_id`, `activity_id`, `seckill_item_id`);

-- ---------- 3. 新建 MQ 本地消息表 ----------
-- 用途：秒杀下单可靠投递凭证，配合 Publisher Confirm + 定时补偿实现消息不丢失
DROP TABLE IF EXISTS `mq_message`;
CREATE TABLE `mq_message` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `message_id`      VARCHAR(128)    NOT NULL                COMMENT '业务唯一ID（userId:activityId:seckillItemId）',
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `activity_id`     BIGINT UNSIGNED NOT NULL,
  `seckill_item_id` BIGINT UNSIGNED NOT NULL               COMMENT '秒杀商品项ID，关联 seckill_item.id（SKU 维度）',
  `spu_id`          BIGINT UNSIGNED DEFAULT NULL           COMMENT '冗余 SPU 维度，便于对账',
  `sku_id`          BIGINT UNSIGNED DEFAULT NULL           COMMENT '冗余 SKU 维度，便于对账',
  `quantity`        INT             NOT NULL DEFAULT 1     COMMENT '购买数量',
  `status`          TINYINT         NOT NULL DEFAULT 0      COMMENT '0-待扣库存 1-待发送 2-已发送 3-发送失败 4-已完成',
  `retry_count`     INT             NOT NULL DEFAULT 0      COMMENT '重试次数',
  `next_retry_time` DATETIME        DEFAULT NULL            COMMENT '下次重试时间',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_status_next_time` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 本地消息表（秒杀下单可靠投递凭证）';
