-- =====================================================================
--  zhiwu-mall 统一库脚本 (合并自原 mall_user / mall_product / mall_order / mall_coupon)
--  MySQL 8.0+ / utf8mb4 / InnoDB
--  重构要点：
--    1. 四库合一为 mall
--    2. 命名按业务域前缀（user_ / product_ / order_ / banner_ / dict）
--    3. 删 undo_log（Seata 已移除）
-- =====================================================================

DROP DATABASE IF EXISTS `mall`;
CREATE DATABASE `mall` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `mall`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
-- 一、用户域
-- =====================================================================

-- 用户主表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT       COMMENT '用户ID',
  `username`       VARCHAR(50)     DEFAULT NULL                   COMMENT '用户名',
  `nickname`       VARCHAR(50)     DEFAULT NULL                   COMMENT '昵称',
  `password`       VARCHAR(100)    DEFAULT NULL                   COMMENT 'MD5 密文',
  `phone`          VARCHAR(20)     DEFAULT NULL                   COMMENT '手机号',
  `avatar`         VARCHAR(255)    DEFAULT NULL                   COMMENT '头像URL',
  `gender`         TINYINT         NOT NULL DEFAULT 0             COMMENT '0未知 1男 2女',
  `balance`        DECIMAL(10,2)   NOT NULL DEFAULT 0.00          COMMENT '模拟余额',
  `member_level`   VARCHAR(20)     NOT NULL DEFAULT 'NORMAL'      COMMENT 'NORMAL/SILVER/GOLD/DIAMOND',
  `growth`         INT             NOT NULL DEFAULT 0             COMMENT '成长值',
  `status`         TINYINT         NOT NULL DEFAULT 1             COMMENT '0禁用 1正常',
  `last_login_at`  DATETIME        DEFAULT NULL,
  `create_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户主表';

-- 登录凭证（支持多端：账号 / 手机 / 微信）
DROP TABLE IF EXISTS `user_auth`;
CREATE TABLE `user_auth` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT UNSIGNED NOT NULL,
  `identity_type`  VARCHAR(20)     NOT NULL                       COMMENT 'USERNAME / PHONE / WECHAT',
  `identifier`     VARCHAR(100)    NOT NULL                       COMMENT '账号/openid',
  `credential`     VARCHAR(255)    DEFAULT NULL                   COMMENT '密码/会话密钥',
  `create_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity` (`identity_type`, `identifier`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录凭证';

-- 收货地址
DROP TABLE IF EXISTS `user_address`;
CREATE TABLE `user_address` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT UNSIGNED NOT NULL,
  `receiver_name`   VARCHAR(50)     NOT NULL,
  `receiver_phone`  VARCHAR(20)     NOT NULL,
  `province`        VARCHAR(50)     DEFAULT NULL,
  `city`            VARCHAR(50)     DEFAULT NULL,
  `district`        VARCHAR(50)     DEFAULT NULL,
  `detail_address`  VARCHAR(255)    NOT NULL,
  `is_default`      TINYINT         NOT NULL DEFAULT 0,
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址';

-- 收藏
DROP TABLE IF EXISTS `user_favorite`;
CREATE TABLE `user_favorite` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `product_id`   BIGINT UNSIGNED NOT NULL,
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏';

-- 足迹
DROP TABLE IF EXISTS `user_footprint`;
CREATE TABLE `user_footprint` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `product_id`   BIGINT UNSIGNED NOT NULL,
  `viewed_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_viewed` (`user_id`, `viewed_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览足迹';

-- 购物车
DROP TABLE IF EXISTS `user_cart`;
CREATE TABLE `user_cart` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `product_id`   BIGINT UNSIGNED NOT NULL,
  `sku_id`       BIGINT UNSIGNED DEFAULT NULL                   COMMENT '规格ID，可空',
  `quantity`     INT             NOT NULL DEFAULT 1,
  `checked`      TINYINT         NOT NULL DEFAULT 1,
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

-- 用户优惠券（合并原 tb_user_coupon）
DROP TABLE IF EXISTS `user_coupon`;
CREATE TABLE `user_coupon` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `coupon_id`    BIGINT UNSIGNED NOT NULL,
  `status`       TINYINT         NOT NULL DEFAULT 0              COMMENT '0未用 1已用 2过期',
  `grab_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `use_time`     DATETIME        DEFAULT NULL,
  `order_id`     BIGINT UNSIGNED DEFAULT NULL                   COMMENT '使用的订单',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_coupon` (`user_id`, `coupon_id`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券';

-- =====================================================================
-- 二、商品域
-- =====================================================================

-- 商品分类（树形，parent_id=0 为顶级）
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `parent_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `name`          VARCHAR(50)     NOT NULL,
  `icon`          VARCHAR(255)    DEFAULT NULL,
  `accent_color`  VARCHAR(20)     DEFAULT NULL                  COMMENT '前端用背景色 #FF7A45',
  `highlight`     VARCHAR(255)    DEFAULT NULL                  COMMENT '分类主推描述',
  `sort_order`    INT             NOT NULL DEFAULT 0,
  `status`        TINYINT         NOT NULL DEFAULT 1            COMMENT '0下线 1正常',
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent_status` (`parent_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

-- 商品主表
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `category_id`     BIGINT UNSIGNED NOT NULL,
  `name`            VARCHAR(150)    NOT NULL,
  `subtitle`        VARCHAR(255)    DEFAULT NULL,
  `description`     TEXT            DEFAULT NULL,
  `main_image`      VARCHAR(255)    DEFAULT NULL,
  `cover_colors`    VARCHAR(255)    DEFAULT NULL                 COMMENT '前端封面渐变色 JSON',
  `price`           DECIMAL(10,2)   NOT NULL,
  `original_price`  DECIMAL(10,2)   DEFAULT NULL,
  `total_stock`     INT             NOT NULL DEFAULT 0,
  `remain_stock`    INT             NOT NULL DEFAULT 0,
  `sales`           INT             NOT NULL DEFAULT 0,
  `tags`            VARCHAR(255)    DEFAULT NULL                 COMMENT '逗号分隔：热卖,新品',
  `is_flash_sale`   TINYINT         NOT NULL DEFAULT 0,
  `status`          TINYINT         NOT NULL DEFAULT 1,
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category_status` (`category_id`, `status`),
  KEY `idx_flash_sale` (`is_flash_sale`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品主表';

-- 商品详情图
DROP TABLE IF EXISTS `product_image`;
CREATE TABLE `product_image` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id`   BIGINT UNSIGNED NOT NULL,
  `image_url`    VARCHAR(255)    NOT NULL,
  `sort_order`   INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品详情图';

-- 商品规格（合并简化版 SKU）
DROP TABLE IF EXISTS `product_sku`;
CREATE TABLE `product_sku` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id`   BIGINT UNSIGNED NOT NULL,
  `spec_text`    VARCHAR(255)    NOT NULL                       COMMENT '红色 / M',
  `color_label`  VARCHAR(50)     DEFAULT NULL                   COMMENT '色卡名：星黛蓝',
  `color_hex`    VARCHAR(20)     DEFAULT NULL                   COMMENT '色卡值：#5B6CFF',
  `price`        DECIMAL(10,2)   DEFAULT NULL,
  `stock`        INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格/SKU';

-- =====================================================================
-- 三、营销域
-- =====================================================================

-- 优惠券模板
DROP TABLE IF EXISTS `coupon`;
CREATE TABLE `coupon` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`             VARCHAR(100)    NOT NULL,
  `coupon_type`       TINYINT         NOT NULL DEFAULT 1        COMMENT '1满减 2折扣',
  `threshold_amount`  DECIMAL(10,2)   NOT NULL DEFAULT 0.00     COMMENT '满多少可用',
  `discount_amount`   DECIMAL(10,2)   NOT NULL DEFAULT 0.00     COMMENT '满减金额',
  `discount_rate`     DECIMAL(3,2)    DEFAULT NULL              COMMENT '0.85 = 8.5折',
  `total_stock`       INT             NOT NULL DEFAULT 0,
  `remain_stock`      INT             NOT NULL DEFAULT 0,
  `per_user_limit`    INT             NOT NULL DEFAULT 1,
  `valid_start`       DATETIME        DEFAULT NULL,
  `valid_end`         DATETIME        DEFAULT NULL,
  `status`            TINYINT         NOT NULL DEFAULT 1,
  `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

-- 首页轮播
DROP TABLE IF EXISTS `banner`;
CREATE TABLE `banner` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`         VARCHAR(100)    NOT NULL,
  `subtitle`      VARCHAR(255)    DEFAULT NULL,
  `cta`           VARCHAR(50)     DEFAULT NULL,
  `image_url`     VARCHAR(255)    DEFAULT NULL,
  `background`    VARCHAR(500)    DEFAULT NULL                  COMMENT 'CSS 渐变',
  `link_type`     VARCHAR(20)     NOT NULL DEFAULT 'NONE'       COMMENT 'NONE/PRODUCT/CATEGORY/URL',
  `link_value`    VARCHAR(255)    DEFAULT NULL,
  `sort_order`    INT             NOT NULL DEFAULT 0,
  `status`        TINYINT         NOT NULL DEFAULT 1,
  `start_time`    DATETIME        DEFAULT NULL,
  `end_time`      DATETIME        DEFAULT NULL,
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页轮播';

-- =====================================================================
-- 四、订单域
-- =====================================================================

-- 订单主表
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`          VARCHAR(32)     NOT NULL                       COMMENT '业务订单号',
  `user_id`           BIGINT UNSIGNED NOT NULL,
  `status`            TINYINT         NOT NULL DEFAULT 0             COMMENT '0未支付 1已支付 2已发货 3已收货 4已评价 5退款 6已取消',
  `total_amount`      DECIMAL(10,2)   NOT NULL,
  `real_amount`       DECIMAL(10,2)   NOT NULL,
  `discount_amount`   DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
  `freight_amount`    DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
  `coupon_id`         BIGINT UNSIGNED DEFAULT NULL,
  `user_coupon_id`    BIGINT UNSIGNED DEFAULT NULL,
  `address_id`        BIGINT UNSIGNED DEFAULT NULL,
  `address_snapshot`  JSON            DEFAULT NULL,
  `remark`            VARCHAR(255)    DEFAULT NULL,
  `paid_at`           DATETIME        DEFAULT NULL,
  `shipped_at`        DATETIME        DEFAULT NULL,
  `received_at`       DATETIME        DEFAULT NULL,
  `cancelled_at`      DATETIME        DEFAULT NULL,
  `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- 订单明细
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`        BIGINT UNSIGNED NOT NULL,
  `product_id`      BIGINT UNSIGNED NOT NULL,
  `sku_id`          BIGINT UNSIGNED DEFAULT NULL,
  `product_name`    VARCHAR(150)    NOT NULL                       COMMENT '快照',
  `product_image`   VARCHAR(255)    DEFAULT NULL,
  `spec_text`       VARCHAR(255)    DEFAULT NULL,
  `price`           DECIMAL(10,2)   NOT NULL,
  `quantity`        INT             NOT NULL,
  `subtotal`        DECIMAL(10,2)   NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

-- 订单状态流转日志
DROP TABLE IF EXISTS `order_status_log`;
CREATE TABLE `order_status_log` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`      BIGINT UNSIGNED NOT NULL,
  `from_status`   TINYINT         DEFAULT NULL,
  `to_status`     TINYINT         NOT NULL,
  `operator`      VARCHAR(20)     DEFAULT 'USER'                  COMMENT 'USER / SYSTEM / ADMIN',
  `remark`        VARCHAR(255)    DEFAULT NULL,
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流转';

-- =====================================================================
-- 五、系统域
-- =====================================================================

-- 字典（订单状态枚举 / 会员等级枚举 / 性别 / 业务配置等）
DROP TABLE IF EXISTS `dict`;
CREATE TABLE `dict` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `dict_type`    VARCHAR(50)     NOT NULL,
  `dict_key`     VARCHAR(50)     NOT NULL,
  `dict_label`   VARCHAR(100)    NOT NULL,
  `sort_order`   INT             NOT NULL DEFAULT 0,
  `extra`        JSON            DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_key` (`dict_type`, `dict_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用字典';

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 初始数据
-- =====================================================================

-- 用户 (密码 123456 = e10adc3949ba59abbe56e057f20f883e)
INSERT INTO `user` (`id`, `username`, `nickname`, `password`, `phone`, `gender`, `balance`, `member_level`, `growth`) VALUES
  (1, 'admin',    '超级管理员',  'e10adc3949ba59abbe56e057f20f883e', '13800000001', 1, 9999.00, 'DIAMOND', 9999),
  (2, 'zhangsan', '张三',        'e10adc3949ba59abbe56e057f20f883e', '13800000002', 1,  500.00, 'GOLD',    2000),
  (3, 'lisi',     '李四',        'e10adc3949ba59abbe56e057f20f883e', '13800000003', 2,  200.00, 'SILVER',   800);

INSERT INTO `user_auth` (`user_id`, `identity_type`, `identifier`, `credential`) VALUES
  (1, 'USERNAME', 'admin',    'e10adc3949ba59abbe56e057f20f883e'),
  (2, 'USERNAME', 'zhangsan', 'e10adc3949ba59abbe56e057f20f883e'),
  (3, 'USERNAME', 'lisi',     'e10adc3949ba59abbe56e057f20f883e'),
  (1, 'PHONE',    '13800000001', NULL),
  (2, 'PHONE',    '13800000002', NULL),
  (3, 'PHONE',    '13800000003', NULL);

-- 分类
INSERT INTO `category` (`id`, `parent_id`, `name`, `icon`, `accent_color`, `highlight`, `sort_order`) VALUES
  (1, 0, '服饰',   '👗', '#FF7A45', '春装新品低至5折', 1),
  (2, 0, '美妆',   '💄', '#FF4D8D', '爆款护肤满199减30', 2),
  (3, 0, '家居',   '🛋', '#3DD9C9', '焕新生活·全场满减', 3),
  (4, 0, '数码',   '🎧', '#5B6CFF', '数码爆款直降800', 4),
  (5, 0, '运动',   '🏃', '#FFB400', '运动出型·装备升级', 5),
  (11, 1, '连衣裙', NULL, NULL, NULL, 1),
  (12, 1, 'T恤',   NULL, NULL, NULL, 2),
  (13, 1, '牛仔裤', NULL, NULL, NULL, 3),
  (14, 1, '防晒衣', NULL, NULL, NULL, 4),
  (15, 1, '短裤',   NULL, NULL, NULL, 5),
  (41, 4, '耳机',   NULL, NULL, NULL, 1),
  (42, 4, '智能设备', NULL, NULL, NULL, 2);

-- 商品
INSERT INTO `product` (`id`, `category_id`, `name`, `subtitle`, `price`, `original_price`, `total_stock`, `remain_stock`, `sales`, `tags`, `is_flash_sale`, `cover_colors`, `main_image`) VALUES
  (1, 11, '春款法式连衣裙', '碎花雪纺 · 显瘦气质款',  199.00, 399.00, 200, 200, 132, '热卖,新品', 1, '["#FFD3B6","#FFAAA5"]', NULL),
  (2, 12, '基础款纯色T恤',   '100%纯棉 · 多色可选',    79.00, 129.00, 500, 500, 856, '百搭',     1, '["#A8E6CF","#3DCCB4"]', NULL),
  (3, 13, '高腰直筒牛仔裤',  '显高显瘦 · 通勤百搭',  169.00, 269.00, 300, 300, 421, '热卖',     0, '["#A1C4FD","#C2E9FB"]', NULL),
  (4, 41, '无线降噪蓝牙耳机', '主动降噪 · 续航 30h',  299.00, 599.00, 100, 100,  98, '新品',     1, '["#0F2027","#203A43"]', NULL),
  (5, 51, '速干运动短裤',    '透气速干 · 弹性舒适',   89.00, 139.00, 400, 400, 233, '运动',     0, '["#FFAFBD","#FFC3A0"]', NULL),
  (6, 14, '轻薄透气防晒衣',  'UPF50+ · 户外必备',    129.00, 199.00, 250, 250, 175, '新品',     0, '["#FBC2EB","#A6C1EE"]', NULL),
  (7, 42, '智能运动手表',    '心率监测 · 50米防水',  599.00, 999.00,  80,  80,  45, '爆款',     1, '["#232526","#414345"]', NULL),
  (8, 31, '折叠收纳箱 加厚',  '环保PP · 70L大容量',   59.00,  99.00, 600, 600,1024, '实用',     0, '["#FCEABB","#F8B500"]', NULL),
  (9, 41, '静音蓝牙耳机',     '入耳式 · 通话降噪',     129.00,229.00, 200, 200, 312, '百搭',     0, '["#FFE1E1","#FFB7B7"]', NULL),
  (10, 12, '基础款白T恤',     '100%纯棉 · 经典版型',   69.00,  99.00, 500, 500, 988, '百搭',     0, '["#E0EAFC","#CFDEF3"]', NULL);

-- 商品规格
INSERT INTO `product_sku` (`product_id`, `spec_text`, `color_label`, `color_hex`, `price`, `stock`) VALUES
  (1, '碎花 / S',  '星黛蓝', '#5B6CFF', 199.00, 100),
  (1, '碎花 / M',  '奶油白', '#FFE3CB', 199.00, 100),
  (4, '星空黑',    '星空黑', '#0F2027', 299.00,  50),
  (4, '珍珠白',    '珍珠白', '#F1F1F1', 299.00,  50);

-- Banner
INSERT INTO `banner` (`id`, `title`, `subtitle`, `cta`, `background`, `link_type`, `link_value`, `sort_order`) VALUES
  (1, '春装新品上市', '全场低至 5 折起 · 满 199 包邮', '立即抢购', 'linear-gradient(135deg, #FFD3B6, #FFAAA5)', 'CATEGORY', '1', 1),
  (2, '限时秒杀',   '每天 12:00 准时开抢',           '查看更多', 'linear-gradient(135deg, #FFEEB7, #FF8A8A)', 'PRODUCT',  '4', 2),
  (3, '会员专享',   '钻石会员 9 折 · 生日礼券',       '加入会员', 'linear-gradient(135deg, #C5C5FF, #8E8EFF)', 'NONE',     NULL, 3);

-- 优惠券
INSERT INTO `coupon` (`id`, `title`, `coupon_type`, `threshold_amount`, `discount_amount`, `total_stock`, `remain_stock`, `valid_start`, `valid_end`) VALUES
  (1, '满 199 减 30',  1, 199.00, 30.00, 1000, 1000, '2026-01-01 00:00:00', '2026-12-31 23:59:59'),
  (2, '满 399 减 80',  1, 399.00, 80.00,  500,  500, '2026-01-01 00:00:00', '2026-12-31 23:59:59'),
  (3, '新人 8.5 折',  2,   0.00,  0.00, 9999, 9999, '2026-01-01 00:00:00', '2026-12-31 23:59:59');

-- 字典
INSERT INTO `dict` (`dict_type`, `dict_key`, `dict_label`, `sort_order`) VALUES
  ('order_status', '0', '待支付', 1),
  ('order_status', '1', '待发货', 2),
  ('order_status', '2', '待收货', 3),
  ('order_status', '3', '待评价', 4),
  ('order_status', '4', '已完成', 5),
  ('order_status', '5', '退款中', 6),
  ('order_status', '6', '已取消', 7),
  ('member_level', 'NORMAL',  '普通会员', 1),
  ('member_level', 'SILVER',  '银卡会员', 2),
  ('member_level', 'GOLD',    '金卡会员', 3),
  ('member_level', 'DIAMOND', '钻石会员', 4),
  ('gender',       '0', '未知', 1),
  ('gender',       '1', '男',   2),
  ('gender',       '2', '女',   3),
  ('coupon_status', '0', '未使用', 1),
  ('coupon_status', '1', '已使用', 2),
  ('coupon_status', '2', '已过期', 3);

-- 收货地址
INSERT INTO `user_address` (`user_id`, `receiver_name`, `receiver_phone`, `province`, `city`, `district`, `detail_address`, `is_default`) VALUES
  (2, '张三', '13800000002', '广东省', '深圳市', '南山区', '科技园路 1 号腾讯大厦 5F', 1),
  (3, '李四', '13800000003', '广东省', '广州市', '天河区', '珠江新城兴民路 222 号',    1);
