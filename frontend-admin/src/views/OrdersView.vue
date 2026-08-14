<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns, SelectOption } from 'naive-ui'
import { NButton, NImage, NSpace, NTag, useMessage } from 'naive-ui'
import { RefreshOutline, SearchOutline } from '@vicons/ionicons5'
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

const money = (value?: number) =>
  new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(Number(value || 0))

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
    title: '订单号',
    key: 'orderNo',
    width: 190,
    render(row) {
      return h('span', { class: 'id-cell' }, row.orderNo)
    },
  },
  {
    title: '商品',
    key: 'itemName',
    minWidth: 260,
    render(row) {
      return h('div', { class: 'goods-cell' }, [
        row.itemImage
          ? h(NImage, { src: row.itemImage, width: 46, height: 46, objectFit: 'cover', class: 'goods-image' })
          : h('div', { class: 'goods-image goods-image--empty' }),
        h('div', { class: 'goods-meta' }, [
          h('strong', row.itemName || '订单商品'),
          h('span', `${row.totalNum || 0} 件 / ${row.itemCount || 0} 类`),
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
        h('strong', row.receiverContact || '-'),
        h('span', row.receiverMobile || '-'),
      ])
    },
  },
  { title: '实付金额', key: 'payMoney', width: 120, render: (row) => money(row.payMoney) },
  {
    title: '来源',
    key: 'orderSource',
    width: 100,
    render(row) {
      return row.orderSource === 2
        ? h(NTag, { size: 'small', type: 'error', round: true }, { default: () => '秒杀' })
        : h(NTag, { size: 'small', round: true }, { default: () => '普通' })
    },
  },
  { title: '状态', key: 'orderState', width: 100, render: (row) => h(OrderStatusTag, { value: row.orderState }) },
  { title: '下单时间', key: 'createTime', width: 170, render: (row) => dateText(row.createTime) },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render(row) {
      return h(NSpace, { size: 8 }, () => [
        h(NButton, { size: 'small', onClick: () => openDetail(row.id) }, { default: () => '详情' }),
        h(
          NButton,
          { size: 'small', type: 'primary', disabled: row.orderState !== 2, onClick: () => openShip(row) },
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
    <div class="page-head">
      <div>
        <h1 class="page-title">订单管理</h1>
        <p class="page-subtitle">查看订单状态、物流信息，并处理待发货订单。</p>
      </div>
    </div>

    <section class="panel panel-pad">
      <n-tabs :value="query.orderState" type="segment" animated @update:value="setState">
        <n-tab v-for="item in stateOptions" :key="String(item.value)" :name="item.value" :tab="item.label">
          <template v-if="item.value === 2 && pendingShipmentCount" #suffix>
            <n-badge :value="pendingShipmentCount" type="warning" />
          </template>
        </n-tab>
      </n-tabs>
      <div class="toolbar order-toolbar">
        <n-input v-model:value="query.keyword" clearable placeholder="订单号 / 收货人 / 手机号" style="width: 260px">
          <template #prefix>
            <n-icon><SearchOutline /></n-icon>
          </template>
        </n-input>
        <n-select v-model:value="query.orderSource" clearable :options="sourceOptions" placeholder="订单来源" style="width: 140px" />
        <n-date-picker v-model:value="dateRange" type="daterange" clearable style="width: 260px" />
        <n-button type="primary" @click="query.page = 1; load()">查询</n-button>
        <n-button @click="reset">重置</n-button>
        <n-button :loading="loading" circle @click="load">
          <template #icon>
            <n-icon><RefreshOutline /></n-icon>
          </template>
        </n-button>
      </div>
    </section>

    <section class="panel">
      <n-data-table :loading="loading" :columns="columns" :data="rows" :bordered="false" />
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

    <n-drawer v-model:show="detailDrawer" :width="760">
      <n-drawer-content title="订单详情">
        <n-spin :show="detailLoading">
          <template v-if="currentOrder">
            <n-descriptions bordered :column="2" label-placement="left">
              <n-descriptions-item label="订单号">{{ currentOrder.orderNo }}</n-descriptions-item>
              <n-descriptions-item label="状态"><OrderStatusTag :value="currentOrder.orderState" /></n-descriptions-item>
              <n-descriptions-item label="用户">{{ currentOrder.nickname || currentOrder.userId }}</n-descriptions-item>
              <n-descriptions-item label="来源">{{ currentOrder.orderSource === 2 ? '秒杀' : '普通' }}</n-descriptions-item>
              <n-descriptions-item label="实付">{{ money(currentOrder.payMoney) }}</n-descriptions-item>
              <n-descriptions-item label="优惠">{{ money(currentOrder.discountAmount) }}</n-descriptions-item>
              <n-descriptions-item label="收货人">{{ currentOrder.receiverContact || '-' }}</n-descriptions-item>
              <n-descriptions-item label="手机号">{{ currentOrder.receiverMobile || '-' }}</n-descriptions-item>
              <n-descriptions-item label="地址" :span="2">{{ currentOrder.receiverAddress || '-' }}</n-descriptions-item>
              <n-descriptions-item label="买家留言" :span="2">{{ currentOrder.buyerMessage || '-' }}</n-descriptions-item>
              <n-descriptions-item label="下单">{{ dateText(currentOrder.createTime) }}</n-descriptions-item>
              <n-descriptions-item label="付款">{{ dateText(currentOrder.paidAt) }}</n-descriptions-item>
              <n-descriptions-item label="发货">{{ dateText(currentOrder.shippedAt) }}</n-descriptions-item>
              <n-descriptions-item label="完成">{{ dateText(currentOrder.completedAt) }}</n-descriptions-item>
            </n-descriptions>

            <n-divider title-placement="left">商品明细</n-divider>
            <div class="detail-items">
              <div v-for="item in currentOrder.items || []" :key="item.id" class="detail-item">
                <n-image v-if="item.image" :src="item.image" width="54" height="54" object-fit="cover" />
                <div class="detail-item-main">
                  <strong>{{ item.name }}</strong>
                  <span>{{ item.attrsText || item.skuCode || '默认规格' }}</span>
                </div>
                <div class="detail-item-pay">
                  <strong>{{ money(item.realPay) }}</strong>
                  <span>x {{ item.quantity }}</span>
                </div>
              </div>
            </div>

            <n-divider title-placement="left">状态流转</n-divider>
            <n-timeline>
              <n-timeline-item
                v-for="log in currentOrder.statusLogs || []"
                :key="log.id"
                :title="`${stateText(log.fromState)} → ${stateText(log.toState)}`"
                :content="`${operatorText(log.operator)}｜${log.remark || '-'}`"
                :time="dateText(log.createTime)"
              />
            </n-timeline>

            <n-divider title-placement="left">物流信息</n-divider>
            <template v-if="currentOrder.logistics">
              <n-descriptions bordered :column="2" label-placement="left">
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
          </template>
        </n-spin>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="shipModal" preset="dialog" title="订单发货" positive-text="保存" negative-text="取消" :loading="shipSaving" @positive-click="submitShip">
      <n-form label-placement="top">
        <n-form-item label="物流公司">
          <n-select v-model:value="shipForm.companyId" :options="companyOptions" placeholder="请选择物流公司" />
        </n-form-item>
        <n-form-item label="运单号">
          <n-input v-model:value="shipForm.logisticsNo" clearable placeholder="请输入快递运单号" />
        </n-form-item>
      </n-form>
    </n-modal>
  </div>
</template>

<style scoped>
.order-toolbar {
  margin-top: 14px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px 16px;
}

.id-cell {
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  white-space: nowrap;
}

.goods-cell,
.detail-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.goods-image {
  width: 46px;
  height: 46px;
  border-radius: 6px;
  overflow: hidden;
  flex: 0 0 auto;
}

.goods-image--empty {
  background: #e7eeee;
}

.goods-meta,
.receiver-cell,
.detail-item-main,
.detail-item-pay {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.goods-meta strong,
.receiver-cell strong,
.detail-item-main strong {
  color: #17212b;
  font-size: 13px;
}

.goods-meta span,
.receiver-cell span,
.detail-item-main span,
.detail-item-pay span {
  color: #758292;
  font-size: 12px;
}

.detail-items {
  display: grid;
  gap: 10px;
}

.detail-item {
  padding: 10px 0;
  border-bottom: 1px solid #edf1f2;
}

.detail-item-main {
  min-width: 0;
  flex: 1;
}

.detail-item-pay {
  align-items: flex-end;
}

.track-timeline {
  margin-top: 14px;
}
</style>
