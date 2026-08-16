-- =====================================================================
--  zhiwu-mall v2 库脚本 — 对齐「小兔鲜儿」小程序前端契约
--  MySQL 8.0+ / utf8mb4 / InnoDB
--
--  设计要点：
--    1. 单库 mall；表按业务域组织（用户 / 商品 / 营销内容 / 订单 / 系统）
--    2. SPU/SKU/规格 完全规范化（spec / spec_value / sku_spec_value），
--       使前端 vk-data-goods-sku-popup 多维规格弹窗可跑通：sku_name_arr 顺序
--       按 spec.sort_order 连接，与 spec_list 顺序一致。
--    3. 订单状态枚举对齐前端 services/constants.js：1待付款 2待发货 3待收货
--       4待评价 5已完成 6已取消（0 仅作列表「全部」筛选，不入库）。
--    4. user/profile 的 fullLocation 由 region 表反查派生；
--       region.code 与前端 provinceCode/cityCode/countyCode 对应。
--    5. 旧 mall.sql 保留作参考，本文件独立。
-- =====================================================================

DROP DATABASE IF EXISTS `mall`;
CREATE DATABASE `mall` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `mall`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
-- 一、用户域
-- =====================================================================

-- 行政区划（供 fullLocation 反查 / 地址编码校验）
DROP TABLE IF EXISTS `region`;
CREATE TABLE `region` (
  `code`         VARCHAR(12)     NOT NULL                       COMMENT '行政区编码（前端 provinceCode/cityCode/countyCode）',
  `name`         VARCHAR(50)     NOT NULL                       COMMENT '行政区名称',
  `parent_code`  VARCHAR(12)     NOT NULL DEFAULT '0'           COMMENT '父级行政区编码，顶级为 0',
  `level`        TINYINT         NOT NULL                       COMMENT '1省 2市 3区县',
  `sort_order`   INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`code`),
  KEY `idx_parent` (`parent_code`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政区划';

-- 用户主表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT       COMMENT '用户ID',
  `account`        VARCHAR(50)     DEFAULT NULL                   COMMENT '账号',
  `nickname`       VARCHAR(50)     DEFAULT NULL                   COMMENT '昵称',
  `password`       VARCHAR(100)    DEFAULT NULL                   COMMENT 'MD5 密文',
  `mobile`         VARCHAR(20)     DEFAULT NULL                   COMMENT '手机号',
  `avatar`         VARCHAR(255)    DEFAULT NULL                   COMMENT '头像URL',
  `gender`         TINYINT         NOT NULL DEFAULT 0             COMMENT '0未知 1男 2女（前端映射 男/女/未知）',
  `birthday`       DATE            DEFAULT NULL                   COMMENT '生日',
  `profession`    VARCHAR(50)     DEFAULT NULL                   COMMENT '职业',
  `province_code`  VARCHAR(12)     DEFAULT NULL                   COMMENT '省份编码',
  `city_code`      VARCHAR(12)     DEFAULT NULL                   COMMENT '城市编码',
  `county_code`   VARCHAR(12)     DEFAULT NULL                   COMMENT '区/县编码',
  `balance`        DECIMAL(10,2)   NOT NULL DEFAULT 0.00          COMMENT '模拟余额',
  `member_level`   VARCHAR(20)     NOT NULL DEFAULT 'NORMAL'      COMMENT 'NORMAL/SILVER/GOLD/DIAMOND',
  `is_admin`       TINYINT         NOT NULL DEFAULT 0             COMMENT '0普通用户 1管理员',
  `growth`         INT             NOT NULL DEFAULT 0             COMMENT '成长值',
  `status`         TINYINT         NOT NULL DEFAULT 1             COMMENT '0禁用 1正常',
  `last_login_at`  DATETIME        DEFAULT NULL,
  `create_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account` (`account`),
  UNIQUE KEY `uk_mobile` (`mobile`)
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
  `receiver`        VARCHAR(50)     NOT NULL                       COMMENT '收货人',
  `contact`         VARCHAR(20)     NOT NULL                       COMMENT '联系方式',
  `province_code`   VARCHAR(12)     NOT NULL                       COMMENT '省份编码',
  `city_code`       VARCHAR(12)     NOT NULL                       COMMENT '城市编码',
  `county_code`     VARCHAR(12)     NOT NULL                       COMMENT '区/县编码',
  `full_location`   VARCHAR(200)    DEFAULT NULL                   COMMENT '完整行政区（由编码派生）',
  `address`         VARCHAR(255)    NOT NULL                       COMMENT '详细地址',
  `postal_code`     VARCHAR(20)     DEFAULT NULL                   COMMENT '邮政编码',
  `address_tags`    VARCHAR(50)     DEFAULT NULL                   COMMENT '地址标签',
  `is_default`      TINYINT         NOT NULL DEFAULT 0             COMMENT '1默认 0否',
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

-- 购物车（按 skuId 维度）
DROP TABLE IF EXISTS `user_cart`;
CREATE TABLE `user_cart` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `sku_id`       BIGINT UNSIGNED NOT NULL                       COMMENT '规格ID（购物车行主键）',
  `count`        INT             NOT NULL DEFAULT 1,
  `selected`     TINYINT         NOT NULL DEFAULT 1             COMMENT '1选中 0未选中',
  `price`        DECIMAL(10,2)   DEFAULT NULL                   COMMENT '加入时价格快照',
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

-- 用户优惠券
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
-- 二、商品域（规范化 SPU / SKU / 规格）
-- =====================================================================

-- 商品分类（树形，parent_id=0 为顶级）
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `parent_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `name`          VARCHAR(50)     NOT NULL,
  `icon`          VARCHAR(255)    DEFAULT NULL                   COMMENT '首页前台分类图标',
  `picture`       VARCHAR(255)    DEFAULT NULL                   COMMENT '分类主图',
  `sort_order`    INT             NOT NULL DEFAULT 0,
  `status`        TINYINT         NOT NULL DEFAULT 1            COMMENT '0下线 1正常',
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent_status` (`parent_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

-- 品牌
DROP TABLE IF EXISTS `brand`;
CREATE TABLE `brand` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(100)    NOT NULL,
  `name_en`       VARCHAR(100)    DEFAULT NULL,
  `logo`          VARCHAR(255)    DEFAULT NULL,
  `sort_order`    INT             NOT NULL DEFAULT 0,
  `status`        TINYINT         NOT NULL DEFAULT 1,
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌';

-- 商品主表（SPU）
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `category_id`     BIGINT UNSIGNED NOT NULL,
  `brand_id`        BIGINT UNSIGNED DEFAULT NULL,
  `spu_code`        VARCHAR(50)     DEFAULT NULL                   COMMENT 'SPU 编码',
  `name`            VARCHAR(150)    NOT NULL,
  `subtitle`        VARCHAR(255)    DEFAULT NULL                   COMMENT '卖点/副标题',
  `description`     TEXT            DEFAULT NULL,
  `price`           DECIMAL(10,2)   NOT NULL                       COMMENT '当前价',
  `old_price`       DECIMAL(10,2)   DEFAULT NULL                   COMMENT '原价',
  `discount`        DECIMAL(3,2)    DEFAULT NULL                   COMMENT '折扣 0.85=8.5折',
  `inventory`       INT             NOT NULL DEFAULT 0             COMMENT 'SPU 总库存（汇总 SKU）',
  `sales_count`     INT             NOT NULL DEFAULT 0             COMMENT '销量',
  `comment_count`   INT             NOT NULL DEFAULT 0             COMMENT '评价数',
  `collect_count`   INT             NOT NULL DEFAULT 0             COMMENT '收藏数',
  `main_videos`     JSON            DEFAULT NULL                   COMMENT '主图视频集合',
  `video_scale`     TINYINT         DEFAULT NULL                   COMMENT '1=1:1或16:9 2=3:4',
  `is_pre_sale`     TINYINT         NOT NULL DEFAULT 0             COMMENT '是否预售',
  `status`          TINYINT         NOT NULL DEFAULT 1            COMMENT '0下架 1上架',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category_status` (`category_id`, `status`),
  KEY `idx_brand` (`brand_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品主表（SPU）';

-- 商品图片（主图 + 详情图）
DROP TABLE IF EXISTS `product_image`;
CREATE TABLE `product_image` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id`   BIGINT UNSIGNED NOT NULL,
  `image_type`   TINYINT         NOT NULL DEFAULT 1              COMMENT '1主图 2详情图',
  `image_url`    VARCHAR(255)    NOT NULL,
  `sort_order`   INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_product_type` (`product_id`, `image_type`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片';

-- 商品详情属性（键值对）
DROP TABLE IF EXISTS `product_property`;
CREATE TABLE `product_property` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id`   BIGINT UNSIGNED NOT NULL,
  `name`         VARCHAR(50)     NOT NULL,
  `value`        VARCHAR(255)    NOT NULL,
  `sort_order`   INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品详情属性';

-- 规格组（如「颜色」「尺寸」，每个 SPU 一组）
DROP TABLE IF EXISTS `spec`;
CREATE TABLE `spec` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id`   BIGINT UNSIGNED NOT NULL,
  `name`         VARCHAR(50)     NOT NULL                       COMMENT '规格组名（颜色）',
  `sort_order`   INT             NOT NULL DEFAULT 0             COMMENT '决定 spec_list 顺序',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规格组';

-- 规格值（如「瓷白色」「8寸」）
DROP TABLE IF EXISTS `spec_value`;
CREATE TABLE `spec_value` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `spec_id`      BIGINT UNSIGNED NOT NULL,
  `name`         VARCHAR(50)     NOT NULL                       COMMENT '规格值名',
  `picture`      VARCHAR(255)    DEFAULT NULL                   COMMENT '该规格值的图片（可选）',
  `sort_order`   INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_spec` (`spec_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规格值';

-- 商品规格（SKU）
DROP TABLE IF EXISTS `product_sku`;
CREATE TABLE `product_sku` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id`   BIGINT UNSIGNED NOT NULL,
  `sku_code`     VARCHAR(50)     DEFAULT NULL,
  `price`        DECIMAL(10,2)   NOT NULL,
  `old_price`    DECIMAL(10,2)   DEFAULT NULL,
  `inventory`    INT             NOT NULL DEFAULT 0             COMMENT 'SKU 库存',
  `picture`      VARCHAR(255)    DEFAULT NULL                   COMMENT 'SKU 图片',
  `status`       TINYINT         NOT NULL DEFAULT 1,
  `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格（SKU）';

-- SKU ↔ 规格值 关联（决定 sku_name_arr 顺序）
DROP TABLE IF EXISTS `sku_spec_value`;
CREATE TABLE `sku_spec_value` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `sku_id`          BIGINT UNSIGNED NOT NULL,
  `spec_id`         BIGINT UNSIGNED NOT NULL,
  `spec_value_id`   BIGINT UNSIGNED NOT NULL,
  `sort_order`      INT             NOT NULL DEFAULT 0          COMMENT '与 spec.sort_order 对齐，保证 sku_name_arr 顺序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_spec` (`sku_id`, `spec_id`),
  KEY `idx_sku` (`sku_id`),
  KEY `idx_spec_value` (`spec_value_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU规格值关联';

-- =====================================================================
-- 三、营销 / 内容域
-- =====================================================================

-- 优惠券模板
DROP TABLE IF EXISTS `coupon`;
CREATE TABLE `coupon` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`             VARCHAR(100)    NOT NULL,
  `coupon_type`       TINYINT         NOT NULL DEFAULT 1        COMMENT '1满减 2折扣',
  `threshold_amount`  DECIMAL(10,2)   NOT NULL DEFAULT 0.00     COMMENT '满多少可用',
  `discount_amount`   DECIMAL(10,2)   NOT NULL DEFAULT 0.00     COMMENT '满减金额',
  `discount_rate`     DECIMAL(3,2)    DEFAULT NULL              COMMENT '0.85=8.5折',
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

-- 首页轮播（distribution_site 1=首页 2=分类页）
DROP TABLE IF EXISTS `banner`;
CREATE TABLE `banner` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`             VARCHAR(100)    NOT NULL,
  `img_url`           VARCHAR(255)    DEFAULT NULL              COMMENT 'banner 图',
  `href_url`          VARCHAR(255)    DEFAULT NULL              COMMENT '跳转链接',
  `type`              TINYINT         NOT NULL DEFAULT 1        COMMENT '1页面 2H5 3小程序',
  `distribution_site` TINYINT         NOT NULL DEFAULT 1        COMMENT '1首页 2分类页',
  `sort_order`        INT             NOT NULL DEFAULT 0,
  `status`            TINYINT         NOT NULL DEFAULT 1,
  `start_time`        DATETIME        DEFAULT NULL,
  `end_time`          DATETIME        DEFAULT NULL,
  `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_site_status_sort` (`distribution_site`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页轮播';

-- 首页热门推荐卡（/home/hot/mutli，4 卡）
DROP TABLE IF EXISTS `home_hot`;
CREATE TABLE `home_hot` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`        VARCHAR(100)    NOT NULL                       COMMENT '推荐标题',
  `alt`          VARCHAR(255)    DEFAULT NULL                   COMMENT '推荐说明',
  `pictures`     JSON            DEFAULT NULL                   COMMENT '图片集合 string[]',
  `target`       VARCHAR(255)    DEFAULT NULL                   COMMENT '跳转地址',
  `type`         VARCHAR(20)     NOT NULL                       COMMENT '推荐类型 → /hot/{key}',
  `sort_order`   INT             NOT NULL DEFAULT 0,
  `status`       TINYINT         NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页热门推荐卡';

-- 热门推荐活动（/hot/{key} 主体）
DROP TABLE IF EXISTS `hot_activity`;
CREATE TABLE `hot_activity` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `activity_key`    VARCHAR(20)     NOT NULL                   COMMENT 'preference/inVogue/oneStop/new',
  `title`           VARCHAR(100)    NOT NULL,
  `banner_picture`  VARCHAR(255)    DEFAULT NULL,
  `sort_order`      INT             NOT NULL DEFAULT 0,
  `status`          TINYINT         NOT NULL DEFAULT 1,
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`activity_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热门推荐活动';

-- 热门推荐子类 Tab（subTypes）
DROP TABLE IF EXISTS `hot_subtype`;
CREATE TABLE `hot_subtype` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `activity_id`   BIGINT UNSIGNED NOT NULL,
  `title`         VARCHAR(100)    NOT NULL,
  `sort_order`    INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_activity` (`activity_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热门推荐子类Tab';

-- 子类 ↔ 商品（subTypes[].goodsItems.items[]）
DROP TABLE IF EXISTS `hot_subtype_product`;
CREATE TABLE `hot_subtype_product` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `subtype_id`   BIGINT UNSIGNED NOT NULL,
  `product_id`   BIGINT UNSIGNED NOT NULL,
  `sort_order`   INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subtype_product` (`subtype_id`, `product_id`),
  KEY `idx_subtype` (`subtype_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热门推荐子类商品';

-- =====================================================================
-- 四、订单域
-- =====================================================================

-- 订单主表
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`            VARCHAR(32)     NOT NULL                 COMMENT '业务订单号',
  `user_id`             BIGINT UNSIGNED NOT NULL,
  `order_state`         TINYINT         NOT NULL DEFAULT 1       COMMENT '1待付款 2待发货 3待收货 4待评价 5已完成 6已取消',
  `total_money`         DECIMAL(10,2)   NOT NULL                 COMMENT '金额合计',
  `pay_money`           DECIMAL(10,2)   NOT NULL                 COMMENT '实付金额',
  `post_fee`            DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '邮费',
  `discount_amount`     DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '优惠金额',
  `pay_type`            TINYINT         DEFAULT NULL            COMMENT '1在线支付 2货到付款',
  `pay_channel`         TINYINT         DEFAULT NULL            COMMENT '1支付宝 2微信',
  `delivery_time_type`  TINYINT         NOT NULL DEFAULT 1      COMMENT '1不限 2工作日 3双休或假日',
  `buyer_message`       VARCHAR(255)    DEFAULT NULL            COMMENT '买家留言',
  `address_id`          BIGINT UNSIGNED DEFAULT NULL,
  `address_snapshot`    JSON            DEFAULT NULL             COMMENT '收货地址快照',
  `receiver_contact`     VARCHAR(50)     DEFAULT NULL            COMMENT '收货人',
  `receiver_mobile`      VARCHAR(20)     DEFAULT NULL            COMMENT '收货人手机',
  `receiver_address`     VARCHAR(255)    DEFAULT NULL            COMMENT '收货人地址',
  `coupon_id`           BIGINT UNSIGNED DEFAULT NULL,
  `user_coupon_id`      BIGINT UNSIGNED DEFAULT NULL,
  `cancel_reason`       VARCHAR(255)    DEFAULT NULL,
  `pay_latest_time`     DATETIME        DEFAULT NULL            COMMENT '付款截止时间（派生 countdown）',
  `paid_at`             DATETIME        DEFAULT NULL            COMMENT '付款时间',
  `shipped_at`          DATETIME        DEFAULT NULL            COMMENT '发货时间',
  `received_at`         DATETIME        DEFAULT NULL            COMMENT '收货时间',
  `completed_at`        DATETIME        DEFAULT NULL            COMMENT '交易完成时间',
  `cancelled_at`        DATETIME        DEFAULT NULL            COMMENT '交易关闭时间',
  `order_source`        TINYINT         NOT NULL DEFAULT 1      COMMENT '订单来源 1普通 2秒杀',
  `activity_id`         BIGINT UNSIGNED DEFAULT NULL            COMMENT '秒杀活动ID（order_source=2 时必填）',
  `seckill_item_id`     BIGINT UNSIGNED DEFAULT NULL            COMMENT '秒杀商品项ID，关联 seckill_item.id',
  `create_time`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_activity_item` (`user_id`, `activity_id`, `seckill_item_id`),
  KEY `idx_user_state` (`user_id`, `order_state`),
  KEY `idx_create_time` (`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- 订单明细
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`        BIGINT UNSIGNED NOT NULL,
  `sku_id`          BIGINT UNSIGNED DEFAULT NULL,
  `spu_id`          BIGINT UNSIGNED NOT NULL,
  `name`            VARCHAR(150)    NOT NULL                    COMMENT '快照',
  `image`           VARCHAR(255)    DEFAULT NULL               COMMENT '快照图',
  `attrs_text`      VARCHAR(255)    DEFAULT NULL               COMMENT '规格文字快照（颜色:瓷白色 尺寸：8寸）',
  `cur_price`       DECIMAL(10,2)   NOT NULL                   COMMENT '实付单价',
  `price`           DECIMAL(10,2)   NOT NULL                   COMMENT '原单价',
  `quantity`        INT             NOT NULL,
  `subtotal`        DECIMAL(10,2)   NOT NULL,
  `real_pay`        DECIMAL(10,2)   NOT NULL                   COMMENT '实付小计',
  `properties`      JSON            DEFAULT NULL               COMMENT '规格快照 [{name,valueName}]',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

-- 订单状态流转日志
DROP TABLE IF EXISTS `order_status_log`;
CREATE TABLE `order_status_log` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`      BIGINT UNSIGNED NOT NULL,
  `from_state`    TINYINT         DEFAULT NULL,
  `to_state`      TINYINT         NOT NULL,
  `operator`      VARCHAR(20)     DEFAULT 'USER'               COMMENT 'USER / SYSTEM / ADMIN',
  `remark`        VARCHAR(255)    DEFAULT NULL,
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流转';

-- 快递公司
DROP TABLE IF EXISTS `logistics_company`;
CREATE TABLE `logistics_company` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(50)     NOT NULL,
  `code`          VARCHAR(20)     DEFAULT NULL,
  `tel`           VARCHAR(30)     DEFAULT NULL,
  `sort_order`    INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快递公司';

-- 订单物流
DROP TABLE IF EXISTS `order_logistics`;
CREATE TABLE `order_logistics` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`      BIGINT UNSIGNED NOT NULL,
  `company_id`    BIGINT UNSIGNED DEFAULT NULL,
  `logistics_no`  VARCHAR(50)     DEFAULT NULL                 COMMENT '快递单号',
  `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单物流';

-- 物流轨迹（list[].{id,text,time}）
DROP TABLE IF EXISTS `order_logistics_track`;
CREATE TABLE `order_logistics_track` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_logistics_id`  BIGINT UNSIGNED NOT NULL,
  `content`             VARCHAR(500)    NOT NULL              COMMENT '物流事件 text',
  `occur_time`          DATETIME        NOT NULL              COMMENT '发生时间 time',
  `sort_order`          INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_logistics` (`order_logistics_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流轨迹';

-- =====================================================================
-- 五、系统域
-- =====================================================================

-- 通用字典
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

-- =====================================================================
-- 五点五、支付域
-- =====================================================================

-- 支付记录表 — 跟踪微信支付交易号、预支付ID、退款信息
DROP TABLE IF EXISTS `pay_record`;
CREATE TABLE `pay_record` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`        BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
  `order_no`        VARCHAR(32)     NOT NULL COMMENT '业务订单号',
  `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `transaction_id`  VARCHAR(64)     DEFAULT NULL COMMENT '微信支付订单号',
  `prepay_id`       VARCHAR(64)     DEFAULT NULL COMMENT '预支付交易会话ID',
  `pay_amount`      DECIMAL(10,2)   NOT NULL COMMENT '支付金额(元)',
  `pay_status`      TINYINT         NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已关闭 3支付失败',
  `refund_no`       VARCHAR(64)     DEFAULT NULL COMMENT '退款单号',
  `refund_id`       VARCHAR(64)     DEFAULT NULL COMMENT '微信退款单号',
  `refund_amount`   DECIMAL(10,2)   DEFAULT NULL COMMENT '退款金额(元)',
  `refund_status`   TINYINT         DEFAULT NULL COMMENT '0退款中 1已退款 2退款异常',
  `refund_reason`   VARCHAR(255)    DEFAULT NULL COMMENT '退款原因',
  `refunded_at`     DATETIME        DEFAULT NULL COMMENT '退款完成时间',
  `paid_at`         DATETIME        DEFAULT NULL COMMENT '支付完成时间',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_transaction_id` (`transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录';

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 初始数据
-- =====================================================================

-- 行政区（少量，供 fullLocation 反查）
INSERT INTO `region` (`code`, `name`, `parent_code`, `level`, `sort_order`) VALUES
  ('44',    '广东省', '0',    1, 19),
  ('4401',  '广州市', '44',   2, 1),
  ('440106', '天河区', '4401', 3, 1),
  ('4403',  '深圳市', '44',   2, 2),
  ('440305', '南山区', '4403', 3, 1),
  ('37',    '山东省', '0',    1, 15),
  ('3701',  '济南市', '37',   2, 1),
  ('370102', '历下区', '3701', 3, 1);

-- 用户（密码 123456 = e10adc3949ba59abbe56e057f20f883e）
INSERT INTO `user` (`id`, `account`, `nickname`, `password`, `mobile`, `gender`, `birthday`, `profession`, `province_code`, `city_code`, `county_code`, `balance`, `member_level`, `is_admin`, `growth`) VALUES
  (1, 'admin',    '超级管理员', 'e10adc3949ba59abbe56e057f20f883e', '13800000001', 1, '1990-01-01', '产品经理', '44', '4403', '440305', 9999.00, 'DIAMOND', 1, 9999),
  (2, 'zhangsan', '张三',       'e10adc3949ba59abbe56e057f20f883e', '13800000002', 1, '1995-05-10', '工程师',   '44', '4403', '440305',  500.00, 'GOLD',    0, 2000),
  (3, 'lisi',     '李四',       'e10adc3949ba59abbe56e057f20f883e', '13800000003', 2, '1998-08-20', '设计师',   '44', '4401', '440106',  200.00, 'SILVER',  0,  800),
  (4, 'xianjian', '小鲜',       'e10adc3949ba59abbe56e057f20f883e', '13123456789', 0, NULL,        NULL,       NULL, NULL,   NULL,     100.00, 'NORMAL',  0,    0);

-- 登录凭证
INSERT INTO `user_auth` (`user_id`, `identity_type`, `identifier`, `credential`) VALUES
  (1, 'USERNAME', 'admin',    'e10adc3949ba59abbe56e057f20f883e'),
  (2, 'USERNAME', 'zhangsan', 'e10adc3949ba59abbe56e057f20f883e'),
  (3, 'USERNAME', 'lisi',     'e10adc3949ba59abbe56e057f20f883e'),
  (4, 'USERNAME', 'xianjian', 'e10adc3949ba59abbe56e057f20f883e'),
  (1, 'PHONE',    '13800000001', NULL),
  (2, 'PHONE',    '13800000002', NULL),
  (3, 'PHONE',    '13800000003', NULL),
  (4, 'PHONE',    '13123456789', NULL);

-- 一级分类（9 个，供 /home/category/mutli）
INSERT INTO `category` (`id`, `parent_id`, `name`, `icon`, `sort_order`) VALUES
  (1, 0, '服饰',   '👗', 1),
  (2, 0, '美妆',   '💄', 2),
  (3, 0, '家居',   '🛋', 3),
  (4, 0, '数码',   '🎧', 4),
  (5, 0, '运动',   '🏃', 5),
  (6, 0, '母婴',   '🍼', 6),
  (7, 0, '生鲜',   '🥦', 7),
  (8, 0, '图书',   '📚', 8),
  (9, 0, '配饰',   '💍', 9);
-- 二级分类
INSERT INTO `category` (`id`, `parent_id`, `name`, `sort_order`) VALUES
  (11, 1, '连衣裙', 1),
  (12, 1, 'T恤',    2),
  (13, 1, '牛仔裤', 3),
  (14, 1, '防晒衣', 4),
  (41, 4, '耳机',    1),
  (42, 4, '智能设备', 2),
  (51, 5, '运动短裤', 1),
  (31, 3, '收纳箱',  1);

-- 品牌
INSERT INTO `brand` (`id`, `name`, `name_en`, `logo`, `sort_order`) VALUES
  (1, '植屋',     'ZHIWU',  'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/brand/zhiwu.png', 1),
  (2, '兔匠',     'TUJIANG', 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/brand/tujiang.png', 2),
  (3, '森野',     'SENYE',   'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/brand/senye.png', 3),
  (4, '极地',     'POLAR',   'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/brand/polar.png', 4);

-- 商品（SPU）— 覆盖多规格
INSERT INTO `product` (`id`, `category_id`, `brand_id`, `spu_code`, `name`, `subtitle`, `price`, `old_price`, `discount`, `inventory`, `sales_count`, `comment_count`, `collect_count`, `is_pre_sale`, `status`) VALUES
  (1, 11, 1, 'SPU-DRESS-001', '春款法式连衣裙',  '碎花雪纺 · 显瘦气质款',   199.00, 399.00, 0.50, 200, 132, 56, 88, 0, 1),
  (2, 12, 1, 'SPU-TEE-002',   '基础款纯色T恤',   '100%纯棉 · 多色可选',     79.00, 129.00, 0.61, 500, 856, 120, 60, 0, 1),
  (3, 13, 2, 'SPU-JEANS-003', '高腰直筒牛仔裤',  '显高显瘦 · 通勤百搭',     169.00, 269.00, 0.63, 300, 421,  90, 45, 0, 1),
  (4, 41, 3, 'SPU-EAR-004',    '无线降噪蓝牙耳机','主动降噪 · 续航 30h',    299.00, 599.00, 0.50, 100,  98, 30, 20, 0, 1),
  (5, 51, 2, 'SPU-SHORT-005',  '速干运动短裤',    '透气速干 · 弹性舒适',     89.00, 139.00, 0.64, 400, 233, 40, 30, 0, 1),
  (6, 14, 1, 'SPU-COAT-006',  '轻薄透气防晒衣',  'UPF50+ · 户外必备',     129.00, 199.00, 0.65, 250, 175, 35, 28, 0, 1),
  (7, 42, 3, 'SPU-WATCH-007', '智能运动手表',    '心率监测 · 50米防水',    599.00, 999.00, 0.60,  80,  45, 12, 18, 0, 1),
  (8, 31, 4, 'SPU-BOX-008',   '折叠收纳箱 加厚',  '环保PP · 70L大容量',    59.00,  99.00, 0.60, 600,1024, 80, 50, 0, 1),
  (9, 41, 3, 'SPU-EAR-009',   '静音蓝牙耳机',    '入耳式 · 通话降噪',      129.00,229.00, 0.56, 200, 312, 60, 35, 0, 1),
  (10, 12, 1,'SPU-TEE-010',   '基础款白T恤',     '100%纯棉 · 经典版型',    69.00,  99.00, 0.70, 500, 988, 150, 70, 0, 1);

-- 商品主图 + 详情图
INSERT INTO `product_image` (`product_id`, `image_type`, `image_url`, `sort_order`) VALUES
  (1, 1, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/1-1.jpg', 1),
  (1, 1, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/1-2.jpg', 2),
  (1, 2, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/1-d1.jpg', 1),
  (2, 1, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/2-1.jpg', 1),
  (2, 2, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/2-d1.jpg', 1),
  (4, 1, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/4-1.jpg', 1),
  (4, 2, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/4-d1.jpg', 1);

-- 商品详情属性（details.properties）
INSERT INTO `product_property` (`product_id`, `name`, `value`, `sort_order`) VALUES
  (1, '材质', '雪纺 100%聚酯纤维', 1),
  (1, '版型', 'A字显瘦', 2),
  (1, '洗涤', '冷水手洗', 3),
  (4, '续航', '30小时', 1),
  (4, '降噪', '主动降噪 ANC', 2),
  (4, '蓝牙', '蓝牙 5.3', 3);

-- 规格组（spec）— 多维规格示例：连衣裙 = 颜色×尺寸；耳机 = 颜色
INSERT INTO `spec` (`id`, `product_id`, `name`, `sort_order`) VALUES
  (1, 1, '颜色', 1),
  (2, 1, '尺寸', 2),
  (3, 2, '颜色', 1),
  (4, 4, '颜色', 1),
  (5, 9, '颜色', 1);

-- 规格值（spec_value）
INSERT INTO `spec_value` (`id`, `spec_id`, `name`, `picture`, `sort_order`) VALUES
  (1,  1, '瓷白色', 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/spec/cibai.jpg', 1),
  (2,  1, '星黛蓝', 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/spec/xinglan.jpg', 2),
  (3,  2, 'S',  NULL, 1),
  (4,  2, 'M',  NULL, 2),
  (5,  2, 'L',  NULL, 3),
  (6,  3, '白', NULL, 1),
  (7,  3, '黑', NULL, 2),
  (8,  3, '灰', NULL, 3),
  (9,  4, '星空黑', 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/spec/black.jpg', 1),
  (10, 4, '珍珠白', 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/spec/white.jpg', 2),
  (11, 5, '雾霾蓝', NULL, 1),
  (12, 5, '樱花粉', NULL, 2);

-- SKU（product_sku）— 连衣裙 6 个（2颜色×3尺寸），T恤 3 个，耳机 2 个
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `price`, `old_price`, `inventory`, `picture`, `status`) VALUES
  (1, 1, 'DRESS-001-CW-S', 199.00, 399.00, 30, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/1-1.jpg', 1),
  (2, 1, 'DRESS-001-CW-M', 199.00, 399.00, 40, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/1-1.jpg', 1),
  (3, 1, 'DRESS-001-XL-S', 199.00, 399.00, 20, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/1-2.jpg', 1),
  (4, 1, 'DRESS-001-XL-M', 199.00, 399.00, 10, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/1-2.jpg', 1),
  (5, 1, 'DRESS-001-CW-L', 199.00, 399.00,  0, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/1-1.jpg', 1),
  (6, 1, 'DRESS-001-XL-L', 199.00, 399.00,  0, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/1-2.jpg', 0),
  (7, 2, 'TEE-002-W-M',   79.00, 129.00, 100, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/2-1.jpg', 1),
  (8, 2, 'TEE-002-BK-M',   79.00, 129.00, 100, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/2-1.jpg', 1),
  (9, 2, 'TEE-002-GR-M',   79.00, 129.00, 100, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/2-1.jpg', 1),
  (10, 4,'EAR-004-BK',     299.00, 599.00, 50, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/4-1.jpg', 1),
  (11, 4,'EAR-004-WH',     299.00, 599.00, 50, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/4-1.jpg', 1),
  (12, 9,'EAR-009-HL',     129.00, 229.00, 80, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/9-1.jpg', 1),
  (13, 9,'EAR-009-PK',     129.00, 229.00, 80, 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/product/9-1.jpg', 1);

-- SKU ↔ 规格值 关联（sku_spec_value，sort_order 与 spec.sort_order 对齐）
-- 连衣裙 SKU 1=瓷白/S, 2=瓷白/M, 3=星黛蓝/S, 4=星黛蓝/M, 5=瓷白/L, 6=星黛蓝/L
INSERT INTO `sku_spec_value` (`sku_id`, `spec_id`, `spec_value_id`, `sort_order`) VALUES
  (1, 1, 1, 1), (1, 2, 3, 2),
  (2, 1, 1, 1), (2, 2, 4, 2),
  (3, 1, 2, 1), (3, 2, 3, 2),
  (4, 1, 2, 1), (4, 2, 4, 2),
  (5, 1, 1, 1), (5, 2, 5, 2),
  (6, 1, 2, 1), (6, 2, 5, 2),
  (7, 3, 6, 1),
  (8, 3, 7, 1),
  (9, 3, 8, 1),
  (10, 4, 9, 1),
  (11, 4, 10, 1),
  (12, 5, 11, 1),
  (13, 5, 12, 1);

-- =====================================================================
-- 补充：为「只有 SPU、没有 SKU」的商品补齐 规格/规格值/SKU/SKU规格关联/图片
-- 原因：原种子只为演示多规格与秒杀给 1/2/4/9 写了 SKU，其余商品仅插了 SPU 行，
--       点进详情时 sku_list 为空 → 前端误判「库存为 0」显示「该商品已抢完」。
-- 涉及：3 牛仔裤、5 运动短裤、6 防晒衣、7 智能手表、8 收纳箱、10 白T恤。
-- 图片为占位外部 URL（沿用原模板 itheima 域名），可自行替换为真实商品图。
-- =====================================================================

-- 规格组（spec，续用 id 6~14）
INSERT INTO `spec` (`id`, `product_id`, `name`, `sort_order`) VALUES
  (6, 3, '颜色', 1),  (7, 3, '尺码', 2),
  (8, 5, '颜色', 1),  (9, 5, '尺码', 2),
  (10, 6, '颜色', 1), (11, 6, '尺码', 2),
  (12, 7, '颜色', 1),
  (13, 8, '容量', 1),
  (14, 10, '尺码', 1);

-- 规格值（spec_value，续用 id 13~34）
INSERT INTO `spec_value` (`id`, `spec_id`, `name`, `picture`, `sort_order`) VALUES
  (13, 6, '浅蓝', NULL, 1), (14, 6, '深蓝', NULL, 2),
  (15, 7, '26', NULL, 1), (16, 7, '28', NULL, 2), (17, 7, '30', NULL, 3),
  (18, 8, '黑色', NULL, 1), (19, 8, '藏青', NULL, 2),
  (20, 9, 'M', NULL, 1), (21, 9, 'L', NULL, 2), (22, 9, 'XL', NULL, 3),
  (23, 10, '白色', NULL, 1), (24, 10, '浅灰', NULL, 2),
  (25, 11, 'M', NULL, 1), (26, 11, 'L', NULL, 2), (27, 11, 'XL', NULL, 3),
  (28, 12, '曜石黑', NULL, 1), (29, 12, '月光银', NULL, 2),
  (30, 13, '70L', NULL, 1), (31, 13, '100L', NULL, 2),
  (32, 14, 'M', NULL, 1), (33, 14, 'L', NULL, 2), (34, 14, 'XL', NULL, 3);

-- SKU（product_sku，续用 id 14~38）
INSERT INTO `product_sku` (`id`, `product_id`, `sku_code`, `price`, `old_price`, `inventory`, `picture`, `status`) VALUES
  (14, 3, 'JEANS-003-LB-26', 169.00, 269.00, 45, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/d8e1bf09f42e472bb96d5e8214419006.png', 1),
  (15, 3, 'JEANS-003-LB-28', 169.00, 269.00, 60, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/d8e1bf09f42e472bb96d5e8214419006.png', 1),
  (16, 3, 'JEANS-003-LB-30', 169.00, 269.00, 55, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/d8e1bf09f42e472bb96d5e8214419006.png', 1),
  (17, 3, 'JEANS-003-DB-26', 169.00, 269.00, 40, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/d8e1bf09f42e472bb96d5e8214419006.png', 1),
  (18, 3, 'JEANS-003-DB-28', 169.00, 269.00, 55, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/d8e1bf09f42e472bb96d5e8214419006.png', 1),
  (19, 3, 'JEANS-003-DB-30', 169.00, 269.00, 45, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/d8e1bf09f42e472bb96d5e8214419006.png', 1),
  (20, 5, 'SHORT-005-BK-M', 89.00, 139.00, 70, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/b90c9fff978348eba09d05f281a25e0d.png', 1),
  (21, 5, 'SHORT-005-BK-L', 89.00, 139.00, 70, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/b90c9fff978348eba09d05f281a25e0d.png', 1),
  (22, 5, 'SHORT-005-BK-XL', 89.00, 139.00, 65, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/b90c9fff978348eba09d05f281a25e0d.png', 1),
  (23, 5, 'SHORT-005-NY-M', 89.00, 139.00, 65, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/b90c9fff978348eba09d05f281a25e0d.png', 1),
  (24, 5, 'SHORT-005-NY-L', 89.00, 139.00, 65, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/b90c9fff978348eba09d05f281a25e0d.png', 1),
  (25, 5, 'SHORT-005-NY-XL', 89.00, 139.00, 65, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/b90c9fff978348eba09d05f281a25e0d.png', 1),
  (26, 6, 'COAT-006-WH-M', 129.00, 199.00, 45, NULL, 1),
  (27, 6, 'COAT-006-WH-L', 129.00, 199.00, 45, NULL, 1),
  (28, 6, 'COAT-006-WH-XL', 129.00, 199.00, 40, NULL, 1),
  (29, 6, 'COAT-006-GY-M', 129.00, 199.00, 40, NULL, 1),
  (30, 6, 'COAT-006-GY-L', 129.00, 199.00, 40, NULL, 1),
  (31, 6, 'COAT-006-GY-XL', 129.00, 199.00, 40, NULL, 1),
  (32, 7, 'WATCH-007-BK', 599.00, 999.00, 40, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/a7efa2ecb417496d932317a3d4a1665b.png', 1),
  (33, 7, 'WATCH-007-SL', 599.00, 999.00, 40, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/a7efa2ecb417496d932317a3d4a1665b.png', 1),
  (34, 8, 'BOX-008-70L', 59.00, 99.00, 300, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260815/04c068e691c6494fb40cd3553e2e27c6.png', 1),
  (35, 8, 'BOX-008-100L', 59.00, 99.00, 300, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260815/04c068e691c6494fb40cd3553e2e27c6.png', 1),
  (36, 10, 'TEE-010-W-M', 69.00, 99.00, 170, NULL, 1),
  (37, 10, 'TEE-010-W-L', 69.00, 99.00, 170, NULL, 1),
  (38, 10, 'TEE-010-W-XL', 69.00, 99.00, 160, NULL, 1);

-- SKU ↔ 规格值（sku_spec_value，sort_order 与 spec.sort_order 对齐）
INSERT INTO `sku_spec_value` (`sku_id`, `spec_id`, `spec_value_id`, `sort_order`) VALUES
  (14, 6, 13, 1), (14, 7, 15, 2),
  (15, 6, 13, 1), (15, 7, 16, 2),
  (16, 6, 13, 1), (16, 7, 17, 2),
  (17, 6, 14, 1), (17, 7, 15, 2),
  (18, 6, 14, 1), (18, 7, 16, 2),
  (19, 6, 14, 1), (19, 7, 17, 2),
  (20, 8, 18, 1), (20, 9, 20, 2),
  (21, 8, 18, 1), (21, 9, 21, 2),
  (22, 8, 18, 1), (22, 9, 22, 2),
  (23, 8, 19, 1), (23, 9, 20, 2),
  (24, 8, 19, 1), (24, 9, 21, 2),
  (25, 8, 19, 1), (25, 9, 22, 2),
  (26, 10, 23, 1), (26, 11, 25, 2),
  (27, 10, 23, 1), (27, 11, 26, 2),
  (28, 10, 23, 1), (28, 11, 27, 2),
  (29, 10, 24, 1), (29, 11, 25, 2),
  (30, 10, 24, 1), (30, 11, 26, 2),
  (31, 10, 24, 1), (31, 11, 27, 2),
  (32, 12, 28, 1),
  (33, 12, 29, 1),
  (34, 13, 30, 1),
  (35, 13, 31, 1),
  (36, 14, 32, 1),
  (37, 14, 33, 1),
  (38, 14, 34, 1);

-- 商品主图（product_image，补 3/5/7/8；6/10 暂无图不插）
INSERT INTO `product_image` (`product_id`, `image_type`, `image_url`, `sort_order`) VALUES
  (3, 1, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/d8e1bf09f42e472bb96d5e8214419006.png', 1),
  (5, 1, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/b90c9fff978348eba09d05f281a25e0d.png', 1),
  (7, 1, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260814/a7efa2ecb417496d932317a3d4a1665b.png', 1),
  (8, 1, 'https://skyhyf.oss-cn-beijing.aliyuncs.com/20260815/04c068e691c6494fb40cd3553e2e27c6.png', 1);

-- 收货地址
INSERT INTO `user_address` (`id`, `user_id`, `receiver`, `contact`, `province_code`, `city_code`, `county_code`, `full_location`, `address`, `postal_code`, `address_tags`, `is_default`) VALUES
  (1, 2, '张三', '13800000002', '44', '4403', '440305', '广东省 深圳市 南山区', '科技园路 1 号腾讯大厦 5F', '518057', '公司', 1),
  (2, 2, '张三', '13800000002', '44', '4403', '440305', '广东省 深圳市 南山区', '后海大道 88 号华润城 3 栋', '518052', '家',   0),
  (3, 3, '李四', '13800000003', '44', '4401', '440106', '广东省 广州市 天河区', '珠江新城兴民路 222 号',     '510620', '公司', 1);

-- 购物车（zhangsan）
INSERT INTO `user_cart` (`user_id`, `sku_id`, `count`, `selected`, `price`) VALUES
  (2, 1, 1, 1, 199.00),
  (2, 7, 2, 1, 79.00);

-- 轮播（5 个：首页 3 + 分类页 2）
INSERT INTO `banner` (`id`, `title`, `img_url`, `href_url`, `type`, `distribution_site`, `sort_order`) VALUES
  (1, '春装新品上市', 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/banner/1.jpg', '/pages/category/category?id=1', 1, 1, 1),
  (2, '限时特惠',     'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/banner/2.jpg', '/pages/goods/goods?id=4',       1, 1, 2),
  (3, '会员专享',     'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/banner/3.jpg', '/pagesMember/settings/settings', 1, 1, 3),
  (4, '数码专场',     'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/banner/4.jpg', '/pages/category/category?id=4', 1, 2, 1),
  (5, '家居焕新',     'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/banner/5.jpg', '/pages/category/category?id=3', 1, 2, 2);

-- 首页热门推荐卡（/home/hot/mutli，4 卡 → /hot/{key}）
INSERT INTO `home_hot` (`id`, `title`, `alt`, `pictures`, `target`, `type`, `sort_order`) VALUES
  (1, '特惠推荐',  '抢先尝鲜 · 新品预告',     JSON_ARRAY('https://pcapi-xiaotuxian-front-devtest.itheima.net/static/hot/1.jpg'), '/pages/hot/hot?type=preference', 'preference', 1),
  (2, '爆款推荐',  '24小时热榜 · 热销总榜',   JSON_ARRAY('https://pcapi-xiaotuxian-front-devtest.itheima.net/static/hot/2.jpg'), '/pages/hot/hot?type=inVogue',     'inVogue',    2),
  (3, '一站买全',  '搞定熊孩子 · 让音质更出众', JSON_ARRAY('https://pcapi-xiaotuxian-front-devtest.itheima.net/static/hot/3.jpg'), '/pages/hot/hot?type=oneStop',    'oneStop',    3),
  (4, '新鲜好物',  '抢先尝鲜 · 新品预告',     JSON_ARRAY('https://pcapi-xiaotuxian-front-devtest.itheima.net/static/hot/4.jpg'), '/pages/hot/hot?type=new',        'new',        4);

-- 热门推荐活动（/hot/{key} 主体）
INSERT INTO `hot_activity` (`id`, `activity_key`, `title`, `banner_picture`, `sort_order`) VALUES
  (1, 'preference', '特惠推荐', 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/hot/banner-1.jpg', 1),
  (2, 'inVogue',    '爆款推荐', 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/hot/banner-2.jpg', 2),
  (3, 'oneStop',    '一站买全', 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/hot/banner-3.jpg', 3),
  (4, 'new',        '新鲜好物', 'https://pcapi-xiaotuxian-front-devtest.itheima.net/static/hot/banner-4.jpg', 4);

-- 热门推荐子类 Tab（每活动 2-3 个）
INSERT INTO `hot_subtype` (`id`, `activity_id`, `title`, `sort_order`) VALUES
  (1,  1, '抢先尝鲜', 1),
  (2,  1, '新品预告', 2),
  (3,  2, '24小时热榜', 1),
  (4,  2, '热销总榜',   2),
  (5,  2, '人气周榜',   3),
  (6,  3, '搞定熊孩子', 1),
  (7,  3, '家里不凌乱', 2),
  (8,  3, '让音质更出众', 3),
  (9,  4, '抢先尝鲜', 1),
  (10, 4, '新品预告', 2);

-- 子类 ↔ 商品
INSERT INTO `hot_subtype_product` (`subtype_id`, `product_id`, `sort_order`) VALUES
  (1, 2, 1), (1, 10, 2),
  (2, 6, 1), (2, 5, 2),
  (3, 4, 1), (3, 9, 2),
  (4, 7, 1), (4, 4, 2),
  (5, 1, 1), (5, 3, 2),
  (6, 8, 1),
  (7, 8, 1),
  (8, 4, 1), (8, 9, 2),
  (9, 2, 1), (9, 10, 2),
  (10, 6, 1), (10, 5, 2);

-- 优惠券
INSERT INTO `coupon` (`id`, `title`, `coupon_type`, `threshold_amount`, `discount_amount`, `discount_rate`, `total_stock`, `remain_stock`, `valid_start`, `valid_end`) VALUES
  (1, '满 199 减 30', 1, 199.00, 30.00, NULL,   1000, 1000, '2026-01-01 00:00:00', '2026-12-31 23:59:59'),
  (2, '满 399 减 80', 1, 399.00, 80.00, NULL,    500,  500, '2026-01-01 00:00:00', '2026-12-31 23:59:59'),
  (3, '新人 8.5 折', 2,   0.00,  0.00, 0.85,   9999, 9999, '2026-01-01 00:00:00', '2026-12-31 23:59:59');

-- 用户优惠券
INSERT INTO `user_coupon` (`user_id`, `coupon_id`, `status`) VALUES
  (2, 1, 0),
  (2, 3, 0),
  (3, 2, 0);

-- 快递公司
INSERT INTO `logistics_company` (`id`, `name`, `code`, `tel`, `sort_order`) VALUES
  (1, '顺丰速运', 'SF',  '95338', 1),
  (2, '中通快递', 'ZTO', '95311', 2),
  (3, '圆通速递', 'YTO', '95554', 3);

-- 字典（订单状态对齐小兔鲜儿 1-6）
INSERT INTO `dict` (`dict_type`, `dict_key`, `dict_label`, `sort_order`) VALUES
  ('order_status', '1', '待付款', 1),
  ('order_status', '2', '待发货', 2),
  ('order_status', '3', '待收货', 3),
  ('order_status', '4', '待评价', 4),
  ('order_status', '5', '已完成', 5),
  ('order_status', '6', '已取消', 6),
  ('member_level', 'NORMAL',  '普通会员', 1),
  ('member_level', 'SILVER',  '银卡会员', 2),
  ('member_level', 'GOLD',    '金卡会员', 3),
  ('member_level', 'DIAMOND', '钻石会员', 4),
  ('gender',       '0', '未知', 1),
  ('gender',       '1', '男',   2),
  ('gender',       '2', '女',   3),
  ('coupon_status', '0', '未使用', 1),
  ('coupon_status', '1', '已使用', 2),
  ('coupon_status', '2', '已过期', 3),
  ('pay_type',      '1', '在线支付', 1),
  ('pay_type',      '2', '货到付款', 2),
  ('pay_channel',   '1', '支付宝', 1),
  ('pay_channel',   '2', '微信',   2),
  ('delivery_time_type', '1', '不限',     1),
  ('delivery_time_type', '2', '工作日',   2),
  ('delivery_time_type', '3', '双休或假日', 3);

-- =====================================================================
-- 六、秒杀活动域
-- =====================================================================

-- 秒杀活动主表
DROP TABLE IF EXISTS `seckill_activity`;
CREATE TABLE `seckill_activity` (
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

-- 秒杀活动商品项（SKU 维度，决定前台展示与下单）
DROP TABLE IF EXISTS `seckill_item`;
CREATE TABLE `seckill_item` (
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

-- MQ 本地消息表（秒杀下单可靠投递凭证，配合 Publisher Confirm + 定时补偿实现消息不丢失）
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

-- 秒杀库存补偿流水（Phase 2：下单失败/支付超时/用户取消/对账偏差触发回补，uk_message_id 防重复回补）
DROP TABLE IF EXISTS `seckill_stock_compensate`;
CREATE TABLE `seckill_stock_compensate` (
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
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀库存补偿流水';

-- 秒杀种子数据（Phase 1 预热/压测用；依赖上方商品种子：product.id=1/4、product_sku.id=1/10）
INSERT INTO `seckill_activity` (`id`, `name`, `start_time`, `end_time`, `enabled`, `remark`) VALUES
  (1, '周年庆秒杀', '2026-08-01 00:00:00', '2026-12-31 23:59:59', 1, '示例活动：Phase 1 压测用，窗口覆盖当前时间');
INSERT INTO `seckill_item` (`id`, `activity_id`, `spu_id`, `sku_id`, `seckill_price`, `seckill_stock`, `limit_per_user`, `sort_order`, `status`) VALUES
  (1, 1, 1, 1,  99.00, 100, 1, 1, 1),
  (2, 1, 4, 10, 199.00,  50, 1, 2, 1);


