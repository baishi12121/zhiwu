<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns, SelectOption } from 'naive-ui'
import { NButton, NImage, NTag, useMessage } from 'naive-ui'
import { SearchOutline } from '@vicons/ionicons5'
import OrderStatusTag from '@/components/OrderStatusTag.vue'
import { getOrder, listLogisticsCompanies, listOrders, shipOrder } from '@/api/orders'
import type { AdminOrder, EntityId, LogisticsCompany } from '@/types/admin'

const message = useMessage()
const loading = ref(false)
const rows = ref<AdminOrder[]>([])
const total = ref(0)
const dateRange = ref<[number, number] | null>(null)
const query = reactive({
  page: 1,
  pageSize: 10,
  orderState: null as number | null,
  orderSource: null as number | null,
  keyword: '',
})

const detailDrawer = ref(false)
const currentOrder = ref<AdminOrder | null>(null)
const detailLoading = ref(false)

const shipModal = ref(false)
const shipSaving = ref(false)
const shippingOrder = ref<AdminOrder | null>(null)
const companies = ref<LogisticsCompany[]>([])
const shipForm = reactive({
  companyId: null as EntityId | null,
  logisticsNo: '',
})

const stateOptions = [
  { label: '全部', value: null },
  { label: '待付款', value: 1 },
  { label: '待发货', value: 2 },
  { label: '待收货', value: 3 },
  { label: '待评价', value: 4 },
  { label: '已完成', value: 5 },
  { label: '已取消', value: 6 },
]

const sourceOptions = [
  { label: '普通', value: 1 },
  { label: '秒杀', value: 2 },
]

const companyOptions = computed<SelectOption[]>(() =>
  companies.value.map((company) => ({
    label: `${company.name}${company.code ? ` (${company.code})` : ''}`,
    value: company.id,
  })),
)

const pendingShipmentCount = computed(() => rows.value.filter((row) => row.orderState === 2).length)

// 价格格式化：¥299.00 强调
const money = (value?: number) => {
  const num = Number(value || 0)
  return `¥${num.toFixed(2)}`
}

const dateText = (value?: string) => (value ? value.replace('T', ' ').slice(0, 19) : '-')

const formatDate = (value: number) => {
  const date = new Date(value)
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const operatorText = (operator?: string) => {
  const map: Record<string, string> = { USER: '用户', SYSTEM: '系统', ADMIN: '管理员' }
  return operator ? map[operator] || operator : '-'
}

const stateText = (state?: number) => stateOptions.find((item) => item.value === state)?.label || '-'

const columns: DataTableColumns<AdminOrder> = [
  {
    title: '订单商品',
    key: 'itemName',
    minWidth: 320,
    render(row) {
      return h('div', { class: 'goods-cell' }, [
        row.itemImage
          ? h(NImage, {
              src: row.itemImage,
              width: 52,
              height: 52,
              objectFit: 'cover',
              class: 'goods-image',
              previewDisabled: true,
            })
          : h('div', { class: 'goods-image goods-image--empty' }, '—'),
        h('div', { class: 'goods-meta' }, [
          h('strong', { class: 'goods-name' }, row.itemName || '订单商品'),
          h('div', { class: 'goods-sub' }, [
            h('span', { class: 'goods-qty' }, `${row.totalNum || 0} 件 / ${row.itemCount || 0} 类`),
          ]),
          h('span', { class: 'goods-order-no' }, row.orderNo),
        ]),
      ])
    },
  },
  {
    title: '收货人',
    key: 'receiverContact',
    width: 150,
    render(row) {
      return h('div', { class: 'receiver-cell' }, [
        h('strong', { class: 'receiver-name' }, row.receiverContact || '-'),
        h('span', { class: 'receiver-mobile' }, row.receiverMobile || '-'),
      ])
    },
  },
  {
    title: '实付金额',
    key: 'payMoney',
    width: 130,
    render: (row) =>
      h('div', { class: 'price-cell' }, [
        h('span', { class: 'price-symbol' }, '¥'),
        h('span', { class: 'price-value' }, Number(row.payMoney || 0).toFixed(2)),
      ]),
  },
  {
    title: '来源',
    key: 'orderSource',
    width: 90,
    render(row) {
      return row.orderSource === 2
        ? h(
            NTag,
            {
              size: 'small',
              round: true,
              bordered: false,
              color: {
                color: 'rgba(245, 158, 11, 0.14)',
                textColor: '#B06F0A',
                borderColor: 'transparent',
              },
            },
            { default: () => '秒杀' },
          )
        : h('span', { class: 'source-text' }, '普通')
    },
  },
  { title: '状态', key: 'orderState', width: 110, render: (row) => h(OrderStatusTag, { value: row.orderState }) },
  { title: '下单时间', key: 'createTime', width: 160, render: (row) => dateText(row.createTime) },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    fixed: 'right',
    render(row) {
      return h('div', { class: 'row-actions' }, [
        h(NButton, { size: 'small', onClick: () => openDetail(row.id) }, { default: () => '详情' }),
        h(
          NButton,
          {
            size: 'small',
            type: 'primary',
            disabled: row.orderState !== 2,
            onClick: () => openShip(row),
          },
          { default: () => '发货' },
        ),
      ])
    },
  },
]

const load = async () => {
  loading.value = true
  try {
    const [start, end] = dateRange.value || []
    const page = await listOrders({
      page: query.page,
      pageSize: query.pageSize,
      orderState: query.orderState,
      orderSource: query.orderSource,
      keyword: query.keyword,
      start: start ? formatDate(start) : undefined,
      end: end ? formatDate(end) : undefined,
    })
    rows.value = page.items
    total.value = page.total
    query.page = page.page
    query.pageSize = page.pageSize
  } catch (error) {
    message.error(error instanceof Error ? error.message : '订单列表加载失败')
  } finally {
    loading.value = false
  }
}

const reset = () => {
  query.keyword = ''
  query.orderState = null
  query.orderSource = null
  dateRange.value = null
  query.page = 1
  load()
}

const loadCompanies = async () => {
  if (companies.value.length) return
  companies.value = await listLogisticsCompanies()
}

const openDetail = async (id: EntityId) => {
  detailLoading.value = true
  detailDrawer.value = true
  try {
    currentOrder.value = await getOrder(id)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '订单详情加载失败')
    detailDrawer.value = false
  } finally {
    detailLoading.value = false
  }
}

const openShip = async (order: AdminOrder) => {
  shippingOrder.value = order
  shipForm.companyId = null
  shipForm.logisticsNo = ''
  await loadCompanies()
  shipModal.value = true
}

const submitShip = async () => {
  if (!shippingOrder.value || !shipForm.companyId || !shipForm.logisticsNo.trim()) {
    message.warning('请选择物流公司并填写运单号')
    return false
  }
  shipSaving.value = true
  try {
    const updated = await shipOrder(shippingOrder.value.id, {
      companyId: shipForm.companyId,
      logisticsNo: shipForm.logisticsNo.trim(),
    })
    message.success('订单已发货')
    shipModal.value = false
    currentOrder.value = updated
    await load()
    return true
  } catch (error) {
    message.error(error instanceof Error ? error.message : '发货失败')
    return false
  } finally {
    shipSaving.value = false
  }
}

const setState = (value: number | null) => {
  query.orderState = value
  query.page = 1
  load()
}

onMounted(() => {
  load()
  loadCompanies().catch(() => undefined)
})
</script>

<template>
  <div class="page">
    <!-- 页面标题 -->
    <header class="page-head">
      <div class="page-head-text">
        <h1 class="page-title">订单管理</h1>
        <p class="page-subtitle">查看订单状态、物流信息，并处理待发货订单。</p>
      </div>
    </header>

    <!-- 状态 Tabs + 搜索区 合并 Card -->
    <section class="panel search-panel">
      <n-tabs
        :value="query.orderState"
        type="line"
        animated
        size="medium"
        class="state-tabs"
        @update:value="setState"
      >
        <n-tab v-for="item in stateOptions" :key="String(item.value)" :name="item.value" :tab="item.label">
          <template v-if="item.value === 2 && pendingShipmentCount" #suffix>
            <n-badge :value="pendingShipmentCount" type="warning" />
          </template>
        </n-tab>
      </n-tabs>

      <div class="search-row search-row--filters">
        <n-input
          v-model:value="query.keyword"
          clearable
          placeholder="搜索订单号 / 收货人 / 手机号"
          class="search-input"
        >
          <template #prefix>
            <n-icon :color="'#98A2B3'"><SearchOutline /></n-icon>
          </template>
        </n-input>
        <n-select
          v-model:value="query.orderSource"
          clearable
          :options="sourceOptions"
          placeholder="全部来源"
          class="filter-select filter-select--sm"
        />
        <n-date-picker
          v-model:value="dateRange"
          type="daterange"
          clearable
          placeholder="选择日期范围"
          class="filter-date-range"
        />
        <div class="search-row-actions">
          <n-button type="primary" @click="query.page = 1; load()">查询</n-button>
          <n-button @click="reset">重置</n-button>
        </div>
      </div>
    </section>

    <!-- 表格 -->
    <section class="panel">
      <n-data-table
        :loading="loading"
        :columns="columns"
        :data="rows"
        :bordered="false"
        :single-line="false"
        :row-key="(row: AdminOrder) => row.id"
        :scroll-x="1000"
      />
      <div class="pagination-bar">
        <n-pagination
          v-model:page="query.page"
          v-model:page-size="query.pageSize"
          show-size-picker
          :page-count="Math.max(1, Math.ceil(total / query.pageSize))"
          :page-sizes="[10, 20, 30, 50]"
          @update:page="load"
          @update:page-size="query.page = 1; load()"
        />
      </div>
    </section>

    <!-- 订单详情 Drawer -->
    <n-drawer v-model:show="detailDrawer" :width="760" :auto-focus="false">
      <n-drawer-content title="订单详情" :native-scrollbar="false" closable>
        <n-spin :show="detailLoading">
          <template v-if="currentOrder">
            <div class="detail-section">
              <h3 class="form-section-title">基础信息</h3>
              <n-descriptions :column="2" label-placement="left" bordered>
                <n-descriptions-item label="订单号">
                  <span class="order-no-text">{{ currentOrder.orderNo }}</span>
                </n-descriptions-item>
                <n-descriptions-item label="状态">
                  <OrderStatusTag :value="currentOrder.orderState" />
                </n-descriptions-item>
                <n-descriptions-item label="用户">{{ currentOrder.nickname || currentOrder.userId }}</n-descriptions-item>
                <n-descriptions-item label="来源">
                  <n-tag
                    v-if="currentOrder.orderSource === 2"
                    size="small"
                    round
                    :bordered="false"
                    :color="{ color: 'rgba(245, 158, 11, 0.14)', textColor: '#B06F0A', borderColor: 'transparent' }"
                  >秒杀</n-tag>
                  <span v-else>普通</span>
                </n-descriptions-item>
                <n-descriptions-item label="实付">
                  <span class="price-cell">
                    <span class="price-symbol">¥</span>
                    <span class="price-value">{{ Number(currentOrder.payMoney || 0).toFixed(2) }}</span>
                  </span>
                </n-descriptions-item>
                <n-descriptions-item label="优惠">{{ money(currentOrder.discountAmount) }}</n-descriptions-item>
              </n-descriptions>
            </div>

            <div class="detail-section">
              <h3 class="form-section-title">收货信息</h3>
              <n-descriptions :column="2" label-placement="left" bordered>
                <n-descriptions-item label="收货人">{{ currentOrder.receiverContact || '-' }}</n-descriptions-item>
                <n-descriptions-item label="手机号">{{ currentOrder.receiverMobile || '-' }}</n-descriptions-item>
                <n-descriptions-item label="地址" :span="2">{{ currentOrder.receiverAddress || '-' }}</n-descriptions-item>
                <n-descriptions-item label="买家留言" :span="2">{{ currentOrder.buyerMessage || '-' }}</n-descriptions-item>
              </n-descriptions>
            </div>

            <div class="detail-section">
              <h3 class="form-section-title">时间线</h3>
              <n-descriptions :column="2" label-placement="left" bordered>
                <n-descriptions-item label="下单">{{ dateText(currentOrder.createTime) }}</n-descriptions-item>
                <n-descriptions-item label="付款">{{ dateText(currentOrder.paidAt) }}</n-descriptions-item>
                <n-descriptions-item label="发货">{{ dateText(currentOrder.shippedAt) }}</n-descriptions-item>
                <n-descriptions-item label="完成">{{ dateText(currentOrder.completedAt) }}</n-descriptions-item>
              </n-descriptions>
            </div>

            <div class="detail-section">
              <h3 class="form-section-title">商品明细</h3>
              <div class="detail-items">
                <div v-for="item in currentOrder.items || []" :key="item.id" class="detail-item">
                  <n-image
                    v-if="item.image"
                    :src="item.image"
                    width="56"
                    height="56"
                    object-fit="cover"
                    preview-disabled
                    class="detail-item-img"
                  />
                  <div v-else class="detail-item-img detail-item-img--empty">—</div>
                  <div class="detail-item-main">
                    <strong class="detail-item-name">{{ item.name }}</strong>
                    <span class="detail-item-attr">{{ item.attrsText || item.skuCode || '默认规格' }}</span>
                  </div>
                  <div class="detail-item-pay">
                    <span class="price-cell">
                      <span class="price-symbol">¥</span>
                      <span class="price-value">{{ Number(item.realPay || 0).toFixed(2) }}</span>
                    </span>
                    <span class="detail-item-qty">x {{ item.quantity }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="detail-section">
              <h3 class="form-section-title">状态流转</h3>
              <n-timeline>
                <n-timeline-item
                  v-for="log in currentOrder.statusLogs || []"
                  :key="log.id"
                  :title="`${stateText(log.fromState)} → ${stateText(log.toState)}`"
                  :content="`${operatorText(log.operator)}｜${log.remark || '-'}`"
                  :time="dateText(log.createTime)"
                />
              </n-timeline>
            </div>

            <div class="detail-section">
              <h3 class="form-section-title">物流信息</h3>
              <template v-if="currentOrder.logistics">
                <n-descriptions :column="2" label-placement="left" bordered>
                  <n-descriptions-item label="物流公司">{{ currentOrder.logistics.companyName || '-' }}</n-descriptions-item>
                  <n-descriptions-item label="运单号">{{ currentOrder.logistics.logisticsNo || '-' }}</n-descriptions-item>
                </n-descriptions>
                <n-timeline class="track-timeline">
                  <n-timeline-item
                    v-for="track in currentOrder.logistics.track || []"
                    :key="track.id"
                    :content="track.content"
                    :time="dateText(track.occurTime)"
                  />
                </n-timeline>
              </template>
              <n-empty v-else description="暂无物流信息" />
            </div>
          </template>
        </n-spin>
      </n-drawer-content>
    </n-drawer>

    <!-- 发货 Modal -->
    <n-modal v-model:show="shipModal" preset="dialog" title="订单发货" :show-icon="false" style="width: 480px">
      <n-form label-placement="top" class="ship-form">
        <n-form-item label="物流公司">
          <n-select
            v-model:value="shipForm.companyId"
            :options="companyOptions"
            placeholder="请选择物流公司"
            filterable
          />
        </n-form-item>
        <n-form-item label="运单号">
          <n-input v-model:value="shipForm.logisticsNo" clearable placeholder="请输入快递运单号" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="shipModal = false">取消</n-button>
        <n-button type="primary" :loading="shipSaving" @click="submitShip">确认发货</n-button>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
/* —— 搜索区 —— */
.search-panel {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.state-tabs {
  margin: 0 -20px;
  padding: 0 20px;
}

:deep(.state-tabs .n-tabs-tab) {
  font-size: 14px;
  font-weight: 500;
  padding: 12px 0;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.search-row--filters {
  width: 100%;
}

.search-input {
  width: 280px;
  max-width: 100%;
}

.filter-select {
  width: 180px;
}

.filter-select--sm {
  width: 140px;
}

.filter-date-range {
  width: 280px;
}

.search-row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

/* —— 订单商品列 —— */
.goods-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.goods-image {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-image);
  overflow: hidden;
  flex-shrink: 0;
  background: var(--color-surface-subtle);
  display: grid;
  place-items: center;
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.goods-image--empty {
  background: var(--color-surface-subtle);
}

.goods-meta {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.goods-name {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: var(--font-weight-medium);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-sub {
  display: flex;
  align-items: center;
  gap: 8px;
}

.goods-qty {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.goods-order-no {
  font-family: var(--font-family-mono);
  font-size: 12px;
  color: var(--color-text-tertiary);
}

/* —— 收货人 —— */
.receiver-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.receiver-name {
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: var(--font-weight-medium);
}

.receiver-mobile {
  font-family: var(--font-family-mono);
  color: var(--color-text-tertiary);
  font-size: 12px;
}

/* —— 价格 —— */
.price-cell {
  display: inline-flex;
  align-items: baseline;
  gap: 1px;
}

.price-symbol {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: var(--font-weight-medium);
}

.price-value {
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: var(--font-weight-semibold);
  letter-spacing: -0.01em;
}

/* —— 来源 —— */
.source-text {
  color: var(--color-text-secondary);
  font-size: 13px;
}

/* —— 操作 —— */
.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* —— 详情 Drawer —— */
.detail-section {
  margin-bottom: 24px;
}

.order-no-text {
  font-family: var(--font-family-mono);
  font-size: 13px;
  color: var(--color-text-secondary);
}

.detail-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-card);
  background: var(--color-surface-subtle);
}

.detail-item-img {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-image);
  overflow: hidden;
  flex-shrink: 0;
  background: var(--color-surface);
  display: grid;
  place-items: center;
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.detail-item-img--empty {
  background: var(--color-surface-subtle);
}

.detail-item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.detail-item-name {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: var(--font-weight-medium);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-item-attr {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.detail-item-pay {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.detail-item-qty {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.track-timeline {
  margin-top: 14px;
}

/* —— 发货表单 —— */
.ship-form {
  padding: 8px 0 4px;
}

/* —— Responsive —— */
@media (max-width: 760px) {
  .search-row--filters {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input,
  .filter-select,
  .filter-select--sm,
  .filter-date-range {
    width: 100%;
  }

  .search-row-actions {
    margin-left: 0;
  }

  .goods-cell {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
