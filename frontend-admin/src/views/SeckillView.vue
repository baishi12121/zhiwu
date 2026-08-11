<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns, SelectOption } from 'naive-ui'
import { NButton, NPopconfirm, NSpace, useMessage } from 'naive-ui'
import { AddOutline, RefreshOutline } from '@vicons/ionicons5'
import StatusTag from '@/components/StatusTag.vue'
import { listProducts, listSkus } from '@/api/products'
import {
  addItem,
  createActivity,
  deleteActivity,
  deleteItem,
  listActivities,
  listItems,
  updateActivity,
  updateActivityEnabled,
  updateItem,
  updateItemStatus,
} from '@/api/seckill'
import type { AdminProduct, AdminProductSku, EntityId, SeckillActivity, SeckillItem } from '@/types/admin'

const message = useMessage()
const loading = ref(false)
const rows = ref<SeckillActivity[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  pageSize: 10,
  enabled: null as number | null,
})

const activityDrawer = ref(false)
const activitySaving = ref(false)
const editingActivityId = ref<EntityId | null>(null)
const activityTime = ref<[number, number] | null>(null)
const activityForm = reactive<Partial<SeckillActivity>>({
  name: '',
  enabled: 1,
  remark: '',
})

const itemDrawer = ref(false)
const itemFormDrawer = ref(false)
const itemSaving = ref(false)
const currentActivity = ref<SeckillActivity | null>(null)
const items = ref<SeckillItem[]>([])
const editingItemId = ref<EntityId | null>(null)
const itemForm = reactive<Partial<SeckillItem>>({
  spuId: undefined,
  skuId: undefined,
  seckillPrice: 0,
  seckillStock: 0,
  limitPerUser: 1,
  sortOrder: 0,
  status: 1,
})

const productLoading = ref(false)
const skuLoading = ref(false)
const products = ref<AdminProduct[]>([])
const productOptions = ref<SelectOption[]>([])
const skuOptions = ref<SelectOption[]>([])
const skus = ref<AdminProductSku[]>([])

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

const itemStatusOptions = [
  { label: '上架', value: 1 },
  { label: '下架', value: 0 },
]

const selectedProduct = computed(() => products.value.find((product) => product.id === itemForm.spuId) || null)
const selectedSku = computed(() => skus.value.find((sku) => sku.id === itemForm.skuId) || null)
const selectedSkuInventory = computed(() => selectedSku.value?.inventory ?? undefined)

const formatDateTime = (value: number) => {
  const date = new Date(value)
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const parseDateTime = (value?: string) => (value ? new Date(value).getTime() : Date.now())
const money = (value?: number) =>
  new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(Number(value || 0))

const columns: DataTableColumns<SeckillActivity> = [
  {
    title: 'ID',
    key: 'id',
    width: 190,
    render(row) {
      return h('span', { class: 'id-cell' }, String(row.id))
    },
  },
  { title: '活动名称', key: 'name', minWidth: 180 },
  { title: '开始时间', key: 'startTime', minWidth: 170 },
  { title: '结束时间', key: 'endTime', minWidth: 170 },
  {
    title: '商品数',
    key: 'itemCount',
    width: 100,
    render: (row) => h('span', { class: row.itemCount ? 'count-hot' : 'count-muted' }, String(row.itemCount || 0)),
  },
  {
    title: '状态',
    key: 'enabled',
    render: (row) => h(StatusTag, { value: row.enabled, activeText: '启用', inactiveText: '禁用' }),
  },
  { title: '备注', key: 'remark', ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 300,
    render(row) {
      return h(NSpace, { size: 8 }, () => [
        h(NButton, { size: 'small', onClick: () => openActivityDrawer(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', onClick: () => openItemDrawer(row) }, { default: () => '商品' }),
        h(
          NButton,
          { size: 'small', type: row.enabled === 1 ? 'warning' : 'success', onClick: () => toggleActivity(row) },
          { default: () => (row.enabled === 1 ? '禁用' : '启用') },
        ),
        h(
          NPopconfirm,
          { onPositiveClick: () => removeActivity(row.id) },
          {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
            default: () => `确认删除活动「${row.name}」？`,
          },
        ),
      ])
    },
  },
]

const itemColumns: DataTableColumns<SeckillItem> = [
  {
    title: 'ID',
    key: 'id',
    width: 190,
    render(row) {
      return h('span', { class: 'id-cell' }, String(row.id))
    },
  },
  { title: '商品', key: 'spuName', render: (row) => row.spuName || row.spuId },
  { title: 'SKU', key: 'skuCode', render: (row) => row.skuCode || row.skuId },
  { title: '原价', key: 'originalPrice', render: (row) => money(row.originalPrice) },
  { title: '秒杀价', key: 'seckillPrice', render: (row) => money(row.seckillPrice) },
  { title: '秒杀库存', key: 'seckillStock' },
  { title: '限购', key: 'limitPerUser' },
  {
    title: '状态',
    key: 'status',
    render: (row) => h(StatusTag, { value: row.status, activeText: '上架', inactiveText: '下架' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 250,
    render(row) {
      return h(NSpace, { size: 8 }, () => [
        h(NButton, { size: 'small', onClick: () => openItemForm(row) }, { default: () => '编辑' }),
        h(
          NButton,
          { size: 'small', type: row.status === 1 ? 'warning' : 'success', onClick: () => toggleItem(row) },
          { default: () => (row.status === 1 ? '下架' : '上架') },
        ),
        h(
          NPopconfirm,
          { onPositiveClick: () => removeItem(row.id) },
          {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '移出' }),
            default: () => '确认移出该秒杀商品？',
          },
        ),
      ])
    },
  },
]

const renderItemEmpty = () =>
  h('div', { class: 'item-empty' }, [
    h('div', { class: 'item-empty-title' }, '当前活动还没有秒杀商品'),
    h('div', { class: 'item-empty-desc' }, '商品只会显示在它加入的活动下面，请先点“加入商品”把库存 SKU 加到这个活动。'),
  ])

const setProductOptions = (list: AdminProduct[]) => {
  products.value = list
  productOptions.value = list.map((product) => ({
    label: `${product.name}｜ID ${product.id}｜库存 ${product.inventory ?? 0}`,
    value: product.id,
  }))
}

const searchProducts = async (keyword = '') => {
  productLoading.value = true
  try {
    const page = await listProducts({
      page: 1,
      pageSize: 30,
      keyword,
      status: 1,
      categoryId: null,
    })
    setProductOptions(page.items)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '商品库存加载失败')
  } finally {
    productLoading.value = false
  }
}

const loadSkuOptions = async (productId: EntityId, keepSkuId?: EntityId) => {
  skuLoading.value = true
  try {
    skus.value = await listSkus(productId)
    skuOptions.value = skus.value.map((sku) => ({
      label: `${sku.skuCode || `SKU ${sku.id}`}｜价格 ${money(sku.price)}｜库存 ${sku.inventory ?? 0}`,
      value: sku.id,
    }))
    if (keepSkuId) {
      itemForm.skuId = keepSkuId
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : 'SKU 库存加载失败')
  } finally {
    skuLoading.value = false
  }
}

const handleProductSelect = async (productId: EntityId | null) => {
  itemForm.spuId = productId || undefined
  itemForm.skuId = undefined
  itemForm.seckillPrice = 0
  itemForm.seckillStock = 0
  skus.value = []
  skuOptions.value = []
  if (productId) {
    await loadSkuOptions(productId)
  }
}

const handleSkuSelect = (skuId: EntityId | null) => {
  itemForm.skuId = skuId || undefined
  const sku = skus.value.find((item) => item.id === skuId)
  if (!sku) return
  itemForm.seckillPrice = Number(itemForm.seckillPrice || sku.price || 0)
  itemForm.seckillStock = Math.min(Number(itemForm.seckillStock || sku.inventory || 0), sku.inventory || 0)
}

const load = async () => {
  loading.value = true
  try {
    const page = await listActivities(query)
    rows.value = page.items
    total.value = page.total
    query.page = page.page
    query.pageSize = page.pageSize
  } catch (error) {
    message.error(error instanceof Error ? error.message : '活动列表加载失败')
  } finally {
    loading.value = false
  }
}

const openActivityDrawer = (activity?: SeckillActivity) => {
  editingActivityId.value = activity?.id || null
  Object.assign(activityForm, {
    name: activity?.name || '',
    enabled: activity?.enabled ?? 1,
    remark: activity?.remark || '',
  })
  activityTime.value = activity
    ? [parseDateTime(activity.startTime), parseDateTime(activity.endTime)]
    : [Date.now(), Date.now() + 2 * 60 * 60 * 1000]
  activityDrawer.value = true
}

const saveActivity = async () => {
  if (!activityForm.name || !activityTime.value) {
    message.warning('请填写活动名称和活动时间')
    return
  }
  activitySaving.value = true
  const payload = {
    ...activityForm,
    startTime: formatDateTime(activityTime.value[0]),
    endTime: formatDateTime(activityTime.value[1]),
  }
  try {
    if (editingActivityId.value) {
      await updateActivity(editingActivityId.value, payload)
      message.success('活动已更新')
    } else {
      await createActivity(payload)
      message.success('活动已创建')
    }
    activityDrawer.value = false
    load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '活动保存失败')
  } finally {
    activitySaving.value = false
  }
}

const toggleActivity = async (row: SeckillActivity) => {
  await updateActivityEnabled(row.id, row.enabled === 1 ? 0 : 1)
  message.success('活动状态已更新')
  load()
}

const removeActivity = async (id: EntityId) => {
  await deleteActivity(id)
  message.success('活动已删除')
  load()
}

const openItemDrawer = async (activity: SeckillActivity) => {
  currentActivity.value = activity
  itemDrawer.value = true
  items.value = await listItems(activity.id)
}

const openItemForm = async (item?: SeckillItem) => {
  editingItemId.value = item?.id || null
  Object.assign(itemForm, {
    spuId: item?.spuId,
    skuId: item?.skuId,
    seckillPrice: item?.seckillPrice || 0,
    seckillStock: item?.seckillStock || 0,
    limitPerUser: item?.limitPerUser || 1,
    sortOrder: item?.sortOrder || 0,
    status: item?.status ?? 1,
  })
  await searchProducts(item?.spuName || '')
  if (item?.spuId) {
    const exists = products.value.some((product) => product.id === item.spuId)
    if (!exists) {
      setProductOptions([{ id: item.spuId, name: item.spuName || `商品 ${item.spuId}`, categoryId: 0, price: 0 }])
    }
    await loadSkuOptions(item.spuId, item.skuId)
  } else {
    skus.value = []
    skuOptions.value = []
  }
  itemFormDrawer.value = true
}

const saveItem = async () => {
  if (!currentActivity.value || !itemForm.spuId || !itemForm.skuId || !itemForm.seckillPrice) {
    message.warning('请选择商品库存里的商品和 SKU，并填写秒杀价')
    return
  }
  if (selectedSkuInventory.value !== undefined && Number(itemForm.seckillStock || 0) > selectedSkuInventory.value) {
    message.warning(`秒杀库存不能超过 SKU 当前库存 ${selectedSkuInventory.value}`)
    return
  }
  itemSaving.value = true
  try {
    if (editingItemId.value) {
      await updateItem(editingItemId.value, itemForm)
      message.success('秒杀商品已更新')
    } else {
      await addItem(currentActivity.value.id, itemForm)
      message.success('秒杀商品已加入')
    }
    itemFormDrawer.value = false
    items.value = await listItems(currentActivity.value.id)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '秒杀商品保存失败')
  } finally {
    itemSaving.value = false
  }
}

const toggleItem = async (item: SeckillItem) => {
  if (!currentActivity.value) return
  await updateItemStatus(item.id, item.status === 1 ? 0 : 1)
  message.success('秒杀商品状态已更新')
  items.value = await listItems(currentActivity.value.id)
}

const removeItem = async (id: EntityId) => {
  if (!currentActivity.value) return
  await deleteItem(id)
  message.success('秒杀商品已移出')
  items.value = await listItems(currentActivity.value.id)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">秒杀管理</h1>
        <p class="page-subtitle">维护秒杀活动、活动商品、秒杀库存和上下架状态。</p>
      </div>
      <n-button type="primary" @click="openActivityDrawer()">
        <template #icon>
          <n-icon><AddOutline /></n-icon>
        </template>
        新建活动
      </n-button>
    </div>

    <section class="panel panel-pad">
      <div class="toolbar">
        <n-select v-model:value="query.enabled" clearable :options="statusOptions" placeholder="活动状态" style="width: 160px" />
        <n-button type="primary" @click="query.page = 1; load()">查询</n-button>
        <n-button @click="query.enabled = null; query.page = 1; load()">重置</n-button>
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

    <n-drawer v-model:show="activityDrawer" :width="520">
      <n-drawer-content :title="editingActivityId ? '编辑秒杀活动' : '新建秒杀活动'">
        <n-form label-placement="top">
          <n-form-item label="活动名称">
            <n-input v-model:value="activityForm.name" />
          </n-form-item>
          <n-form-item label="活动时间">
            <n-date-picker v-model:value="activityTime" type="datetimerange" clearable style="width: 100%" />
          </n-form-item>
          <n-form-item label="状态">
            <n-select v-model:value="activityForm.enabled" :options="statusOptions" />
          </n-form-item>
          <n-form-item label="备注">
            <n-input v-model:value="activityForm.remark" type="textarea" :autosize="{ minRows: 3 }" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="activityDrawer = false">取消</n-button>
            <n-button type="primary" :loading="activitySaving" @click="saveActivity">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="itemDrawer" :width="960">
      <n-drawer-content :title="`活动商品 - ${currentActivity?.name || ''}`">
        <n-button type="primary" style="margin-bottom: 12px" @click="openItemForm()">加入商品</n-button>
        <n-data-table :columns="itemColumns" :data="items" :bordered="false" :render-empty="renderItemEmpty" />
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="itemFormDrawer" :width="520">
      <n-drawer-content :title="editingItemId ? '编辑秒杀商品' : '加入秒杀商品'">
        <n-form label-placement="top">
          <n-form-item label="从商品库存选择商品">
            <n-select
              v-model:value="itemForm.spuId"
              filterable
              remote
              clearable
              :loading="productLoading"
              :options="productOptions"
              placeholder="输入商品名称或编码搜索"
              @search="searchProducts"
              @update:value="handleProductSelect"
              @focus="searchProducts('')"
            />
          </n-form-item>
          <n-form-item label="选择 SKU">
            <n-select
              v-model:value="itemForm.skuId"
              filterable
              clearable
              :disabled="!itemForm.spuId"
              :loading="skuLoading"
              :options="skuOptions"
              placeholder="先选择商品，再选择 SKU 库存"
              @update:value="handleSkuSelect"
            />
          </n-form-item>
          <div v-if="selectedProduct || selectedSku" class="stock-hint">
            <span v-if="selectedProduct">商品库存：{{ selectedProduct.inventory ?? 0 }}</span>
            <span v-if="selectedSku">SKU 库存：{{ selectedSku.inventory ?? 0 }}</span>
            <span v-if="selectedSku">原价：{{ money(selectedSku.price) }}</span>
          </div>
          <n-form-item label="秒杀价">
            <n-input-number v-model:value="itemForm.seckillPrice" :min="0.01" style="width: 100%" />
          </n-form-item>
          <n-form-item label="秒杀库存">
            <n-input-number
              v-model:value="itemForm.seckillStock"
              :min="0"
              :max="selectedSkuInventory"
              style="width: 100%"
            />
          </n-form-item>
          <n-form-item label="限购数量">
            <n-input-number v-model:value="itemForm.limitPerUser" :min="1" style="width: 100%" />
          </n-form-item>
          <n-form-item label="排序">
            <n-input-number v-model:value="itemForm.sortOrder" :min="0" style="width: 100%" />
          </n-form-item>
          <n-form-item label="状态">
            <n-select v-model:value="itemForm.status" :options="itemStatusOptions" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="itemFormDrawer = false">取消</n-button>
            <n-button type="primary" :loading="itemSaving" @click="saveItem">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<style scoped>
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px 16px;
}

.id-cell {
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  white-space: nowrap;
}

.count-hot {
  color: #0f766e;
  font-weight: 700;
}

.count-muted {
  color: #94a3b8;
}

.item-empty {
  padding: 42px 16px;
  text-align: center;
}

.item-empty-title {
  color: #1f2937;
  font-size: 15px;
  font-weight: 700;
}

.item-empty-desc {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.stock-hint {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: -4px 0 16px;
}

.stock-hint span {
  padding: 4px 8px;
  border: 1px solid #d8e7e3;
  border-radius: 6px;
  color: #0f766e;
  background: #f1faf7;
  font-size: 12px;
}
</style>
