-- =====================================================================
--  mall 库补丁：新增 user_token 表（用户登录会话表）
--  使用方法：登录 mall 库后执行本脚本
--    USE mall;
--    SOURCE /path/to/mall_user_token.sql;
-- =====================================================================

USE `mall`;

DROP TABLE IF EXISTS `user_token`;
CREATE TABLE `user_token` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`      BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
  `token`        VARCHAR(64)     NOT NULL                COMMENT '登录凭证(UUID)',
  `client`       VARCHAR(20)     NOT NULL DEFAULT 'H5'    COMMENT '登录端: H5/MP/APP',
  `expire_at`    DATETIME        NOT NULL                COMMENT '过期时间',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录会话表';
