# Admin Order Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase 1 admin order management module described in `doc/管理后台订单管理设计方案.md`.

**Architecture:** `mall-admin-service` owns the admin-facing order APIs and writes the shared `mall` database in a local transaction. `frontend-admin` adds an Orders view that consumes `/admin/orders/**` through the existing Axios and Naive UI patterns.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, Maven, Vue 3, TypeScript, Vite, Naive UI.

## Global Constraints

- Reuse existing `{ code, message, data }` and `PageResult` contracts.
- Use `/admin/orders` routes through the existing gateway/admin auth path.
- Shipping only transitions `order_state` from `2` to `3`.
- Do not add new database tables or columns.
- Phase 2 items such as admin cancel/refund, third-party logistics sync, CSV export, and logistics company CRUD stay out of scope.

---

### Task 1: Backend Order API

**Files:**
- Create: `mall-admin-service/src/main/java/com/hyf/malladminservice/controller/AdminOrderController.java`
- Create: `mall-admin-service/src/main/java/com/hyf/malladminservice/service/AdminOrderService.java`
- Create: `mall-admin-service/src/main/java/com/hyf/malladminservice/service/impl/AdminOrderServiceImpl.java`
- Create: `mall-admin-service/src/main/java/com/hyf/malladminservice/dto/request/OrderShipRequest.java`
- Create: order/logistics entities and mappers under `mall-admin-service/src/main/java/com/hyf/malladminservice/{entity,mapper}/`
- Test: `mall-admin-service/src/test/java/com/hyf/malladminservice/service/impl/AdminOrderServiceImplTest.java`

**Interfaces:**
- Produces: `GET /admin/orders`, `GET /admin/orders/{id}`, `PUT /admin/orders/{id}/ship`, `GET /admin/orders/logistics/companies`
- Produces: `AdminOrderService.ship(Long orderId, OrderShipRequest req): AdminOrder`

- [ ] Write a failing service test proving shipping rejects non-pending-shipment orders and does not write logistics side effects.
- [ ] Run the focused Maven test and confirm it fails because `AdminOrderServiceImpl` does not exist yet.
- [ ] Add entities, mappers, service, and controller.
- [ ] Run the focused Maven test and confirm it passes.
- [ ] Run `mvn -f E:/zhiwu-mall/mall-admin-service/pom.xml test`.

### Task 2: Frontend Orders View

**Files:**
- Create: `frontend-admin/src/api/orders.ts`
- Modify: `frontend-admin/src/types/admin.ts`
- Create: `frontend-admin/src/components/OrderStatusTag.vue`
- Create: `frontend-admin/src/views/OrdersView.vue`
- Modify: `frontend-admin/src/router/index.ts`
- Modify: `frontend-admin/src/layouts/AdminLayout.vue`

**Interfaces:**
- Consumes: backend `AdminOrder`, `LogisticsCompany`, and shipping endpoints.
- Produces: `/orders` admin route with list, filters, detail drawer, and ship modal.

- [ ] Add TypeScript API wrappers and types.
- [ ] Add the six-state status tag component.
- [ ] Add the order table, filters, detail drawer, and ship modal.
- [ ] Add router and sidebar entries.
- [ ] Run `npm run build` in `frontend-admin`.

### Task 3: Final Verification

**Files:**
- Verify backend and frontend build output only.

- [ ] Run backend Maven tests for `mall-admin-service`.
- [ ] Run frontend TypeScript/Vite build.
- [ ] Review `git diff` for accidental unrelated changes.
