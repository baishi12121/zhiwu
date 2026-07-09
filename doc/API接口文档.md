# zhiwu-mall API 接口文档

> 本文档定义前端 `mall-uniapp` 与后端 5 个微服务的所有接口契约。  
> 后端基地址（开发环境）：`http://localhost:8080`（由 `mall-gateway-service` 统一入口）  
> 当网关未启动时，前端直连各服务端口（8081~8084）。

---

## 1. 通用约定

### 1.1 响应格式

所有接口统一返回 `Result<T>`：

```json
{
  "code": 200,
  "message": "ok",
  "data": { ... }
}
```

- `code = 200` 成功；其他表示业务异常
- 业务异常约定：
  - `400` 参数校验失败
  - `401` 未登录
  - `403` 无权限
  - `404` 资源不存在
  - `429` 流控触发（Sentinel）
  - `500` 内部异常

### 1.2 鉴权

登录后接口需在请求头携带：

```
Authorization: Bearer <accessToken>
```

- `accessToken` 有效期 30 分钟
- `refreshToken` 有效期 7 天，调用 `/api/auth/refreshToken` 续期
- 后端通过 `mall-user-service` 的统一拦截器校验（暂放各服务本地，迁网关后下放网关）

### 1.3 路径前缀

- 业务接口：`/api/...`
- 内部接口（Feign 专用）：`/internal/...`（不入网关）

### 1.4 错误码

| 业务码 | 含义 | 典型场景 |
|---|---|---|
| 1001 | 用户名或密码错误 | 登录 |
| 1002 | 用户已存在 | 注册 |
| 1003 | Token 失效 | 自动刷新 |
| 2001 | 库存不足 | 扣库存 |
| 2002 | 商品已下架 | 下单 |
| 3001 | 优惠券已抢完 | 秒杀 |
| 3002 | 已抢过该券 | 秒杀 |
| 3003 | 优惠券已使用 | 核销 |
| 4001 | 订单状态非法流转 | 支付/取消 |

---

## 2. 认证 Auth（mall-user-service）

### 2.1 密码登录

`POST /api/auth/login`

| 项 | 值 |
|---|---|
| 鉴权 | 否 |
| 限流 | 无 |

请求体：
```json
{ "username": "zhangsan", "password": "123456" }
```

响应 `data`：
```json
{
  "userId": 2,
  "nickname": "张三",
  "avatar": "https://...",
  "memberLevel": "GOLD",
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "expiresIn": 1800
}
```

### 2.2 发送短信验证码

`POST /api/auth/sms/send`

请求体：
```json
{ "phone": "13800000002" }
```

响应：成功只返回 `code=200`，`data=null`。

### 2.3 短信登录

`POST /api/auth/sms/login`

请求体：
```json
{ "phone": "13800000002", "code": "839201" }
```

响应同 2.1。

### 2.4 微信登录

`POST /api/auth/wxLogin`

请求体：
```json
{ "code": "wx_jscode_xxx", "nickname": "微信昵称", "avatar": "https://..." }
```

> 服务端用 `code` 调微信 `code2Session` 拿 `openid`，第一次登录自动注册。

### 2.5 刷新 Token

`POST /api/auth/refreshToken`

请求体：
```json
{ "refreshToken": "eyJhbGciOi..." }
```

响应：返回新的 `accessToken` + `refreshToken`。

### 2.6 退出登录

`GET /api/auth/logout`

响应：`code=200`，服务端失效 token。

### 2.7 注册

`POST /api/auth/register`

请求体：
```json
{ "username": "newuser", "password": "123456", "phone": "13800000099", "nickname": "新人" }
```

---

## 3. 我的 Me（mall-user-service）

> 全部需要鉴权（除 `GET /api/me/services`）。

### 3.1 我的资料

`GET /api/me/profile`

响应 `data`：
```json
{
  "userId": 2,
  "username": "zhangsan",
  "nickname": "张三",
  "phone": "13800000002",
  "avatar": "https://...",
  "gender": 1,
  "memberLevel": "GOLD",
  "memberDesc": "金卡会员",
  "growth": 2000,
  "balance": 500.00
}
```

### 3.2 修改资料

`PUT /api/me/profile`

请求体：
```json
{ "nickname": "新昵称", "avatar": "https://...", "gender": 1 }
```

### 3.3 上传头像

`POST /api/me/avatar`（Content-Type: multipart/form-data）

请求：`file` 字段。

响应 `data`：`{ "avatarUrl": "https://..." }`

### 3.4 修改密码

`PUT /api/me/password`

请求体：
```json
{ "oldPassword": "123456", "newPassword": "abc123" }
```

### 3.5 订单状态统计

`GET /api/me/orderStats`

响应 `data`（供前端 tabBar 我的页订单卡片用）：
```json
{
  "unpaid": 1,
  "unshipped": 2,
  "unreceived": 0,
  "unreviewed": 3,
  "refund": 0
}
```

### 3.6 常用服务（前端 mock 兼容）

`GET /api/me/services`

响应 `data`：服务图标列表。前端可保留 mock，本接口只用于后端化场景。

---

## 4. 收货地址（mall-user-service）

### 4.1 地址列表

`GET /api/me/addresses`

响应 `data`（默认地址排前）：
```json
[
  {
    "id": 1, "receiverName": "张三", "receiverPhone": "138...",
    "province": "广东省", "city": "深圳市", "district": "南山区",
    "detailAddress": "科技园路 1 号", "isDefault": 1
  }
]
```

### 4.2 新增地址

`POST /api/me/addresses`

请求体：
```json
{
  "receiverName": "李四", "receiverPhone": "13900000001",
  "province": "广东省", "city": "深圳市", "district": "福田区",
  "detailAddress": "中心广场 88 号", "isDefault": 0
}
```

> 若 `isDefault=1`，服务端自动把同用户其他地址置为 0。

### 4.3 更新地址

`PUT /api/me/addresses/{id}`

请求体同 4.2（全部或部分字段）。

### 4.4 删除地址

`DELETE /api/me/addresses/{id}`

### 4.5 设置默认

`PUT /api/me/addresses/{id}/default`

---

## 5. 收藏 / 足迹（mall-user-service / mall-product-service）

> 收藏写入 user-service；足迹直接走 product-service（点击商品详情时调用）。

### 5.1 收藏列表

`GET /api/me/favorites?page=1&pageSize=20`

响应 `data`：
```json
{
  "total": 8,
  "list": [
    { "favoriteId": 1, "productId": 4, "productName": "无线降噪蓝牙耳机", "price": 299.00, "mainImage": "...", "createTime": "2026-07-01 10:00:00" }
  ]
}
```

### 5.2 添加收藏

`POST /api/me/favorites`

请求体：`{ "productId": 4 }`

### 5.3 取消收藏

`DELETE /api/me/favorites/{productId}`

### 5.4 足迹列表

`GET /api/me/footprints?page=1&pageSize=20`

响应结构同 5.1，字段 `viewedAt` 替代 `createTime`。

### 5.5 清空足迹

`DELETE /api/me/footprints`

---

## 6. 首页 Home（mall-product-service）

> 无需鉴权。

### 6.1 轮播图

`GET /api/home/banners`

响应 `data`：
```json
[
  {
    "id": 1, "title": "春装新品上市", "subtitle": "全场低至 5 折起",
    "cta": "立即抢购", "background": "linear-gradient(...)",
    "linkType": "CATEGORY", "linkValue": "1"
  }
]
```

### 6.2 首页快捷分类

`GET /api/home/quickCategories`

响应 `data`（5~10 个，前端 quick-grid 用）：
```json
[
  { "id": 1, "name": "服饰", "icon": "👗", "accent": "#FF7A45" },
  { "id": 2, "name": "美妆", "icon": "💄", "accent": "#FF4D8D" }
]
```

> 第一个固定为 `{ "id": "all", "name": "全部", "icon": "≡", "accent": "#5B6CFF" }`，点击跳转分类 tabBar。

### 6.3 限时秒杀

`GET /api/home/flashSale?limit=6`

响应 `data`：商品精简对象数组
```json
[
  {
    "id": 1, "name": "春款法式连衣裙", "price": 199.00, "originalPrice": 399.00,
    "mainImage": "...", "label": "SALE", "colors": ["#FFD3B6","#FFAAA5"]
  }
]
```

### 6.4 猜你喜欢

`GET /api/home/recommend?page=1&pageSize=10`

响应 `data`：`{ "total": N, "list": [商品精简对象] }`

---

## 7. 分类 Category（mall-product-service）

### 7.1 分类树

`GET /api/categories`

响应 `data`：
```json
[
  {
    "id": 1, "name": "服饰", "icon": "👗", "accentColor": "#FF7A45",
    "highlight": "春装新品低至5折",
    "children": [
      { "id": 11, "name": "连衣裙", "icon": null, "accentColor": null }
    ]
  }
]
```

### 7.2 分类详情

`GET /api/categories/{id}`

响应 `data`：单个分类对象（含父类信息 `parentName`）。

---

## 8. 商品 Product（mall-product-service）

### 8.1 商品分页/搜索/筛选

`GET /api/products`

| 参数 | 必填 | 说明 |
|---|---|---|
| `page` | 否 | 默认 1 |
| `pageSize` | 否 | 默认 20，最大 50 |
| `keyword` | 否 | 模糊匹配 name/subtitle |
| `categoryId` | 否 | 分类 ID（含子类） |
| `sort` | 否 | `default` / `price_asc` / `price_desc` / `sales_desc` / `newest` |
| `isFlashSale` | 否 | 0/1 |

响应 `data`：
```json
{
  "total": 100,
  "page": 1,
  "pageSize": 20,
  "list": [
    {
      "id": 1, "name": "春款法式连衣裙", "subtitle": "碎花雪纺",
      "price": 199.00, "originalPrice": 399.00, "sales": 132,
      "mainImage": "...", "tags": "热卖,新品",
      "isFlashSale": 1, "colors": ["#FFD3B6","#FFAAA5"]
    }
  ]
}
```

### 8.2 商品详情

`GET /api/products/{id}`

响应 `data`：
```json
{
  "id": 1,
  "name": "春款法式连衣裙",
  "subtitle": "碎花雪纺 · 显瘦气质款",
  "description": "...",
  "mainImage": "...",
  "images": ["...", "..."],
  "price": 199.00,
  "originalPrice": 399.00,
  "totalStock": 200,
  "remainStock": 200,
  "sales": 132,
  "tags": "热卖,新品",
  "skus": [
    { "id": 1, "specText": "碎花 / S", "colorLabel": "星黛蓝", "colorHex": "#5B6CFF", "price": 199.00, "stock": 100 }
  ],
  "isFavorite": false,
  "isFlashSale": 1,
  "categoryId": 11,
  "categoryName": "连衣裙"
}
```

### 8.3 商品规格

`GET /api/products/{id}/skus`

响应 `data`：8.2 中的 `skus` 数组。

### 8.4 热门榜单

`GET /api/products/hot?topN=10`

响应 `data`：
```json
[
  { "productId": 4, "name": "无线降噪蓝牙耳机", "price": 299.00, "score": 1850, "rank": 1 }
]
```

### 8.5 点击埋点（写热榜）

`POST /api/products/click/{id}`（需鉴权，可选）

> 服务端会推 MQ 消息给热榜消费者，最终落 Redis ZSET。

### 8.6 内部：扣减库存（Feign 专用）

`POST /internal/products/decrease-stock?id={id}&count={count}`

- 调用方：`mall-order-service` 创建订单时
- 返回：`{ "success": true, "remaining": 199 }`
- 失败：`{ "success": false, "message": "库存不足" }`，HTTP 400

---

## 9. 购物车 Cart（mall-user-service）

> 鉴权。

### 9.1 购物车列表

`GET /api/cart`

响应 `data`：
```json
[
  {
    "id": 1, "productId": 1, "skuId": 1, "productName": "春款法式连衣裙",
    "productImage": "...", "specText": "碎花 / S", "price": 199.00,
    "quantity": 2, "checked": 1, "stock": 100
  }
]
```

### 9.2 加入购物车

`POST /api/cart`

请求体：
```json
{ "productId": 1, "skuId": 1, "quantity": 1 }
```

> 同一 `skuId` 已存在则数量叠加。

### 9.3 更新数量 / 勾选

`PUT /api/cart/{id}`

请求体：
```json
{ "quantity": 3, "checked": 1 }
```

> 至少传一个字段。

### 9.4 删除一项

`DELETE /api/cart/{id}`

### 9.5 清空购物车

`DELETE /api/cart`

### 9.6 全选/反选

`PUT /api/cart/checked?checked=0`

---

## 10. 结算 Checkout（mall-order-service）

### 10.1 结算预览

`POST /api/orders/preview`

请求体：
```json
{
  "cartIds": [1, 2],
  "addressId": 1,
  "couponId": 1
}
```

响应 `data`：
```json
{
  "items": [
    { "productId": 1, "skuId": 1, "productName": "春款法式连衣裙", "price": 199.00, "quantity": 2, "subtotal": 398.00 }
  ],
  "totalAmount": 398.00,
  "discountAmount": 30.00,
  "freightAmount": 0.00,
  "realAmount": 368.00,
  "address": { "id": 1, "receiverName": "...", "detailAddress": "..." },
  "coupon":  { "id": 1, "title": "满 199 减 30" }
}
```

> 不写库，仅计算价格和校验库存。

---

## 11. 订单 Order（mall-order-service）

> 全部需鉴权。

### 11.1 创建订单

`POST /api/orders`

请求体同 10.1。

响应 `data`：
```json
{
  "orderId": 1001,
  "orderNo": "2026070815230001",
  "totalAmount": 398.00,
  "realAmount": 368.00,
  "status": 0
}
```

> 服务端流程：
> 1. 校验地址、库存、优惠券（一个 `@Transactional`）
> 2. 扣库存（直接 UPDATE product.remain_stock，**不**经 Feign 内部接口）
> 3. 写 `order` + `order_item`
> 4. 标记 `user_coupon.status=1` 占用（实际核销放到支付回调）
> 5. 推 MQ `product.order.queue` 写热榜
> 6. 返回订单号

### 11.2 订单列表

`GET /api/orders?status=0&page=1&pageSize=10`

| 参数 | 必填 | 说明 |
|---|---|---|
| `status` | 否 | 不传=全部，0=待支付 ... |
| `page` / `pageSize` | 否 | 分页 |

响应 `data`：
```json
{
  "total": 8,
  "list": [
    {
      "orderId": 1001, "orderNo": "...", "status": 0, "statusLabel": "待支付",
      "totalAmount": 398.00, "realAmount": 368.00,
      "items": [
        { "productId": 1, "productName": "春款法式连衣裙", "productImage": "...", "price": 199.00, "quantity": 2, "specText": "碎花 / S" }
      ],
      "createTime": "2026-07-08 15:23:00"
    }
  ]
}
```

### 11.3 订单详情

`GET /api/orders/{id}`

响应 `data`：单个订单对象（含 `addressSnapshot`、`statusLog`）。

### 11.4 支付（Mock）

`PUT /api/orders/{id}/pay`

> Mock 实现，秒成功；状态 `0→1→2`（实际拆成两步：支付回调发货；mock 一次性到 2）。

### 11.5 取消订单

`PUT /api/orders/{id}/cancel`

> 仅 `status=0` 可取消；触发：退库存、退优惠券。

### 11.6 确认收货

`PUT /api/orders/{id}/confirm`

> 仅 `status=2` 可确认，状态 `→3`。

### 11.7 评价

`POST /api/orders/{id}/review`

请求体：
```json
{
  "items": [
    { "orderItemId": 1, "rating": 5, "content": "很好穿", "images": ["..."] }
  ]
}
```

> 当前版本仅记录评价，**不**单独建评价表（评价数据落到 `order_item.review_*` 字段，二期做独立表）。

---

## 12. 优惠券 Coupon（mall-coupon-service）

### 12.1 可领券列表

`GET /api/coupons?page=1&pageSize=10`

响应 `data`：
```json
{
  "total": 3,
  "list": [
    {
      "id": 1, "title": "满 199 减 30", "couponType": 1,
      "thresholdAmount": 199.00, "discountAmount": 30.00,
      "remainStock": 980, "validStart": "...", "validEnd": "...",
      "grabbed": false
    }
  ]
}
```

> `grabbed` 表示当前用户是否已抢过。

### 12.2 我的优惠券

`GET /api/me/coupons?status=0&page=1&pageSize=10`

| `status` | 含义 |
|---|---|
| 0 | 未使用（默认） |
| 1 | 已使用 |
| 2 | 已过期 |

### 12.3 抢券（秒杀）

`POST /api/coupons/{id}/grab`（需鉴权）

> Sentinel 限流保护；Redis Lua 原子扣减；MQ 异步落库。

响应 `data`：
```json
{ "success": true, "message": "抢券成功" }
```

失败场景：
- 抢完：`{ "success": false, "code": 3001, "message": "已抢完" }`
- 重复：`{ "success": false, "code": 3002, "message": "已抢过" }`
- 限流：`code=429`

### 12.4 核销（结算时由 order-service 调用）

`POST /api/coupons/use`（内部接口）

请求体：
```json
{ "userId": 2, "couponId": 1, "orderId": 1001 }
```

---

## 13. 通用 Common

### 13.1 字典

`GET /api/dict/{type}`

| `type` | 含义 |
|---|---|
| `order_status` | 订单状态 |
| `member_level` | 会员等级 |
| `gender` | 性别 |
| `coupon_status` | 券状态 |

### 13.2 文件上传

`POST /api/upload`（multipart/form-data）

请求：`file` 字段。

响应 `data`：`{ "url": "https://..." }`

> 本地存储到 `/data/upload/yyyyMMdd/`，返回 CDN/静态服务 URL。开发期可返回 `http://localhost:8080/static/...`。

### 13.3 健康检查

`GET /api/health`

响应 `data`：
```json
{
  "status": "UP",
  "services": {
    "mysql": "UP", "redis": "UP", "rabbitmq": "UP", "nacos": "UP"
  }
}
```

---

## 14. 服务归属矩阵

| 接口前缀 | 服务 | 端口 |
|---|---|---|
| `/api/auth/**` | mall-user-service | 8081 |
| `/api/me/**`（user/address/favorite/cart） | mall-user-service | 8081 |
| `/api/home/**`、`/api/categories/**`、`/api/products/**`、`/api/upload/**`、`/api/dict/**` | mall-product-service | 8084 |
| `/api/me/footprints` | mall-product-service | 8084 |
| `/api/orders/**`、`/api/cart` | mall-user-service | 8081（购物车）与 8082（订单）—— **实际看代码分配** |
| `/api/coupons/**` | mall-coupon-service | 8083 |
| `/internal/**` | Feign 内部专用 | 8082→8084 |

> 真实落点会受服务重构影响。最终分配以网关路由配置为准。

---

## 15. 内部调用（Feign）

> 不暴露网关，仅服务间调用。

| 调用方 | 被调用方 | Path | 用途 |
|---|---|---|---|
| mall-order-service | mall-product-service | `POST /internal/products/decrease-stock` | 扣库存 |
| mall-product-service | mall-user-service | `GET /internal/users/{id}` | 校验用户（可选） |

未来扩展：
- `POST /internal/coupons/occupy` —— order 占券
- `POST /internal/coupons/release` —— 取消订单退券
