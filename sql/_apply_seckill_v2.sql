-- =====================================================================
--  秒杀 schema 修订与补全脚本 v2（增量，作用于已有 mall 库，不重建其他表）
--  适用场景：库中已有业务数据，仅需补齐/修正秒杀相关表与字段，不触碰其他表
--  与 v1(_apply_seckill.sql) 的关系：本脚本可独立执行（v1 执行过与否均可）
--  幂等：可重复执行，已完成的步骤自动跳过
--
--  主要变更（对照已落库的 v1 schema）：
--    1. mq_message：product_id(SPU 维度) → seckill_item_id(SKU 维度)，
--       并补 spu_id / sku_id / quantity 冗余列（用动态 SQL 判断列是否存在，幂等）
--    2. 新增 seckill_stock_compensate（秒杀库存补偿流水，Phase 2）
--    3. 补秒杀种子数据（活动 + 商品项，供 Phase 1 预热/压测）
-- =====================================================================

USE `mall`;
SET NAMES utf8mb4;

-- =====================================================================
-- 1. 秒杀活动主表（不存在才建，与 init.sql 一致）
-- =====================================================================
CREATE TABLE IF NOT EXISTS `seckill_activity` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(100)    NOT NULL                       COMMENT '活动名称',
  `start_time`    DATETIME        NOT NULL                       COMMENT '开始时间',
  `end_time`      DATETIME        NOT NULL                       COMMENT '结束时间',
  `enabled`       TINYINT         NOT NULL DEFAULT 1             COMMENT '0禁用 1启用',
  `remark`        VARCHAR(255)    DEFAULT NULL                   COMMENT '备注',
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_enabled_time` (`enabled`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

-- =====================================================================
-- 2. 秒杀活动商品项（SKU 维度，不存在才建）
-- =====================================================================
CREATE TABLE IF NOT EXISTS `seckill_item` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `activity_id`     BIGINT UNSIGNED NOT NULL,
  `spu_id`          BIGINT UNSIGNED NOT NULL,
  `sku_id`          BIGINT UNSIGNED NOT NULL,
  `seckill_price`   DECIMAL(10,2)   NOT NULL                    COMMENT '秒杀价',
  `seckill_stock`   INT             NOT NULL DEFAULT 0           COMMENT '秒杀库存（独立于 SKU 原库存）',
  `limit_per_user`  INT             NOT NULL DEFAULT 1           COMMENT '每人限购',
  `sort_order`      INT             NOT NULL DEFAULT 0,
  `status`          TINYINT         NOT NULL DEFAULT 1           COMMENT '0下架 1上架',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_sku` (`activity_id`, `sku_id`),
  KEY `idx_activity_sort` (`activity_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动商品项';

-- =====================================================================
-- 3. 秒杀种子数据（幂等 INSERT IGNORE，供 Phase 1 预热/压测）
--    假定商品/SKU 来自 init.sql 种子（product.id=1/4、product_sku.id=1/10）；
--    若库中商品 id 不同，请按实际修改或自行从管理后台创建。
-- =====================================================================
INSERT IGNORE INTO `seckill_activity` (`id`, `name`, `start_time`, `end_time`, `enabled`, `remark`) VALUES
  (1, '周年庆秒杀', '2026-08-01 00:00:00', '2026-12-31 23:59:59', 1, '示例活动：Phase 1 压测用，窗口覆盖当前时间');

INSERT IGNORE INTO `seckill_item` (`id`, `activity_id`, `spu_id`, `sku_id`, `seckill_price`, `seckill_stock`, `limit_per_user`, `sort_order`, `status`) VALUES
  (1, 1, 1, 1,  99.00, 100, 1, 1, 1),
  (2, 1, 4, 10, 199.00,  50, 1, 2, 1);

-- =====================================================================
-- 4. MQ 本地消息表：修正为 SKU 维度（seckill_item_id）
--    4.1 表不存在 → 直接建正确 schema
--    4.2 表已存在且仍是旧版 product_id → 增量迁移：
--        加 seckill_item_id → 回填 → 补冗余列 → 删 product_id
--    （注：秒杀未上线，mq_message 基本为空表；若非空，回填按「同活动下
--      product_id 对应首个 seckill_item」映射——SPU 维度无法唯一对应 SKU，
--      需人工复核，回填不彻底的场景会跳过 NOT NULL 收紧并保留旧列）
-- =====================================================================
CREATE TABLE IF NOT EXISTS `mq_message` (
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

-- 4.2 旧版迁移（以下动态 SQL 均幂等，已迁移过会自动跳过）
-- ① 加 seckill_item_id（仅当旧版 product_id 存在且尚无该列时）
SET @sql_add_item_id := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_message' AND COLUMN_NAME = 'product_id')
  AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_message' AND COLUMN_NAME = 'seckill_item_id'),
  'ALTER TABLE `mq_message` ADD COLUMN `seckill_item_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''秒杀商品项ID（SKU 维度）'' AFTER `activity_id`',
  'SELECT 1'
);
PREPARE stmt FROM @sql_add_item_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ② 回填 seckill_item_id（仅当旧版 product_id 列仍存在时；product_id(SPU) → 首个匹配 seckill_item.id）
SET @sql_backfill := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_message' AND COLUMN_NAME = 'product_id'),
  'UPDATE `mq_message` m
   LEFT JOIN (
     SELECT si.`id`, si.`activity_id`, si.`spu_id`
     FROM `seckill_item` si
     JOIN (SELECT `activity_id`, `spu_id`, MIN(`id`) AS `mid` FROM `seckill_item` GROUP BY `activity_id`, `spu_id`) t
       ON si.`activity_id` = t.`activity_id` AND si.`spu_id` = t.`spu_id`
   ) si ON si.`activity_id` = m.`activity_id` AND si.`spu_id` = m.`product_id`
   SET m.`seckill_item_id` = si.`id`
   WHERE m.`seckill_item_id` IS NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql_backfill; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ③ 补冗余列 spu_id / sku_id / quantity（不存在才加）
SET @sql_add_spu := IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_message' AND COLUMN_NAME = 'spu_id'),
  'ALTER TABLE `mq_message` ADD COLUMN `spu_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''冗余 SPU 维度，便于对账'' AFTER `seckill_item_id`',
  'SELECT 1');
PREPARE stmt FROM @sql_add_spu; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_add_sku := IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_message' AND COLUMN_NAME = 'sku_id'),
  'ALTER TABLE `mq_message` ADD COLUMN `sku_id` BIGINT UNSIGNED DEFAULT NULL COMMENT ''冗余 SKU 维度，便于对账'' AFTER `spu_id`',
  'SELECT 1');
PREPARE stmt FROM @sql_add_sku; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_add_qty := IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_message' AND COLUMN_NAME = 'quantity'),
  'ALTER TABLE `mq_message` ADD COLUMN `quantity` INT NOT NULL DEFAULT 1 COMMENT ''购买数量'' AFTER `sku_id`',
  'SELECT 1');
PREPARE stmt FROM @sql_add_qty; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ④ 收尾：无残留 NULL 时收紧 NOT NULL 并删除旧 product_id 列
--    （若回填不彻底导致仍有 NULL，则跳过该步、保留旧列，供人工复核）
SET @sql_finalize := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_message' AND COLUMN_NAME = 'product_id')
  AND (SELECT COUNT(*) FROM `mq_message` WHERE `seckill_item_id` IS NULL) = 0,
  'ALTER TABLE `mq_message` MODIFY `seckill_item_id` BIGINT UNSIGNED NOT NULL, DROP COLUMN `product_id`',
  'SELECT 1'
);
PREPARE stmt FROM @sql_finalize; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ⑤ 同步 message_id 列注释（v1 遗留为 productId 表述；本步幂等，可安全重复）
ALTER TABLE `mq_message` MODIFY `message_id` VARCHAR(128) NOT NULL COMMENT '业务唯一ID（userId:activityId:seckillItemId）';

-- 4.3 迁移结果自查（应看到 seckill_item_id / spu_id / sku_id / quantity，且无 product_id）
SELECT COLUMN_NAME AS `mq_message 列`, COLUMN_TYPE, COLUMN_COMMENT AS `说明`
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mq_message'
ORDER BY ORDINAL_POSITION;

-- =====================================================================
-- 5. 秒杀库存补偿流水（Phase 2，不存在才建）
-- =====================================================================
CREATE TABLE IF NOT EXISTS `seckill_stock_compensate` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `message_id`      VARCHAR(128)    NOT NULL                COMMENT '关联业务messageId',
  `activity_id`     BIGINT UNSIGNED NOT NULL,
  `seckill_item_id` BIGINT UNSIGNED NOT NULL,
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `quantity`        INT             NOT NULL,
  `compensate_type` TINYINT         NOT NULL                COMMENT '1下单失败 2支付超时 3用户取消 4对账偏差',
  `status`          TINYINT         NOT NULL DEFAULT 0      COMMENT '0待处理 1已完成 2失败',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id_type` (`message_id`, `compensate_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀库存补偿流水';
