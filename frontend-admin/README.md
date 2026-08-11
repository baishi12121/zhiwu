# 植屋商城后台管理端

技术栈：Vue 3 + Vite + TypeScript + Pinia + Vue Router + Naive UI + vicons + ECharts。

## 运行

```bash
npm install
npm run dev
```

默认开发端口为 `5174`，Vite 会把 `/api/**` 代理到 `http://localhost:8080`，也就是 `mall-gateway-service`。

如需直连其他网关地址：

```bash
VITE_PROXY_TARGET=http://localhost:8080 npm run dev
```

如需构建时使用固定 API 地址，可设置：

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run build
```

## 已对接接口

- `POST /admin/login`、`GET /admin/logout`、`GET /admin/profile`
- `GET /admin/sales/overview`
- `GET /admin/sales/products`
- `GET /admin/sales/categories`
- `GET /admin/sales/trend/daily`
- `GET/POST/PUT/DELETE /admin/products/**`
- `GET/POST/PUT/DELETE /admin/skus/**`
- `GET /admin/categories`
- `GET/POST/PUT/DELETE /admin/seckill/**`
- `GET /admin/users`、`GET /admin/users/{id}`、用户状态与等级更新
