<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns, DropdownOption, SelectOption } from 'naive-ui'
import { NButton, NDropdown, NIcon, useDialog, useMessage } from 'naive-ui'
import {
  AddOutline,
  ArrowDownOutline,
  CartOutline,
  CreateOutline,
  PricetagsOutline,
  TrashOutline,
} from '@vicons/ionicons5'
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
const dialog = useDialog()
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
const itemLoading = ref(false)
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

const moneyParts = (value?: number) => ({
  symbol: '¥',
  amount: Number(value || 0).toFixed(2),
})

const formatDate = (value?: string) => {
  if (!value) return '-'
  return value.replace('T', ' ')
}

const columns: DataTableColumns<SeckillActivity> = [
  {
    title: '活动',
    key: 'name',
    minWidth: 260,
    render(row) {
      return h('div', { class: 'activity-cell' }, [
        h('strong', { class: 'activity-name' }, row.name),
        h('span', { class: 'activity-id' }, `ID ${row.id}`),
      ])
    },
  },
  {
    title: '开始时间',
    key: 'startTime',
    minWidth: 170,
    render: (row) => h('span', { class: 'cell-text' }, formatDate(row.startTime)),
  },
  {
    title: '结束时间',
    key: 'endTime',
    minWidth: 170,
    render: (row) => h('span', { class: 'cell-text' }, formatDate(row.endTime)),
  },
  {
    title: '商品数',
    key: 'itemCount',
    width: 100,
    render: (row) =>
      h('span', { class: row.itemCount ? 'count-hot' : 'count-muted' }, String(row.itemCount || 0)),
  },
  {
    title: '状态',
    key: 'enabled',
    width: 110,
    render: (row) => h(StatusTag, { value: row.enabled, activeText: '启用', inactiveText: '禁用' }),
  },
  { title: '备注', key: 'remark', ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'cell-text' }, row.remark || '-') },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    fixed: 'right',
    render(row) {
      const dropdownOptions: DropdownOption[] = [
        {
          label: '管理商品',
          key: 'manage-items',
          icon: () => h(NIcon, null, { default: () => h(CartOutline) }),
        },
        {
          label: row.enabled === 1 ? '禁用活动' : '启用活动',
          key: 'toggle-status',
          icon: () => h(NIcon, null, { default: () => h(PricetagsOutline) }),
        },
        { type: 'divider', key: 'd1' },
        {
          label: '删除活动',
          key: 'delete',
          icon: () => h(NIcon, { color: '#D03050' }, { default: () => h(TrashOutline) }),
        },
      ]
      return h('div', { class: 'row-actions' }, [
        h(
          NButton,
          { size: 'small', type: 'primary', onClick: () => openActivityDrawer(row) },
          { default: () => '编辑', icon: () => h(NIcon, null, { default: () => h(CreateOutline) }) },
        ),
        h(
          NDropdown,
          {
            trigger: 'click',
            options: dropdownOptions,
            onSelect: (key: string) => {
              if (key === 'manage-items') openItemDrawer(row)
              else if (key === 'toggle-status') toggleActivity(row)
              else if (key === 'delete') confirmDeleteActivity(row)
            },
          },
          {
            default: () =>
              h(
                NButton,
                { size: 'small', quaternary: true },
                {
                  default: () => '更多',
                  icon: () => h(NIcon, null, { default: () => h(ArrowDownOutline) }),
                },
              ),
          },
        ),
      ])
    },
  },
]

const itemColumns: DataTableColumns<SeckillItem> = [
  {
    title: '商品',
    key: 'spuName',
    minWidth: 220,
    render(row) {
      return h('div', { class: 'item-cell' }, [
        h('strong', { class: 'item-name' }, row.spuName || `SPU ${row.spuId}`),
        h('span', { class: 'item-sku' }, `SKU ${row.skuCode || row.skuId}`),
      ])
    },
  },
  {
    title: '原价',
    key: 'originalPrice',
    width: 120,
    render: (row) => {
      const parts = moneyParts(row.originalPrice)
      return h('span', { class: 'price-cell price-cell--muted' }, [
        h('span', { class: 'price-symbol' }, parts.symbol),
        h('span', { class: 'price-amount' }, parts.amount),
      ])
    },
  },
  {
    title: '秒杀价',
    key: 'seckillPrice',
    width: 120,
    render: (row) => {
      const parts = moneyParts(row.seckillPrice)
      return h('span', { class: 'price-cell' }, [
        h('span', { class: 'price-symbol' }, parts.symbol),
        h('span', { class: 'price-amount price-amount--hot' }, parts.amount),
      ])
    },
  },
  {
    title: '秒杀库存',
    key: 'seckillStock',
    width: 110,
    render: (row) => {
      const stock = Number(row.seckillStock || 0)
      const cls = stock === 0 ? 'stock-danger' : stock < 10 ? 'stock-warning' : 'stock-normal'
      return h('span', { class: `count-cell ${cls}` }, String(stock))
    },
  },
  { title: '限购', key: 'limitPerUser', width: 90, render: (row) => h('span', { class: 'cell-text' }, String(row.limitPerUser ?? 1)) },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render: (row) => h(StatusTag, { value: row.status, activeText: '上架', inactiveText: '下架' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    fixed: 'right',
    render(row) {
      const dropdownOptions: DropdownOption[] = [
        {
          label: row.status === 1 ? '下架' : '上架',
          key: 'toggle-status',
          icon: () => h(NIcon, null, { default: () => h(PricetagsOutline) }),
        },
        { type: 'divider', key: 'd1' },
        {
          label: '移出活动',
          key: 'delete',
          icon: () => h(NIcon, { color: '#D03050' }, { default: () => h(TrashOutline) }),
        },
      ]
      return h('div', { class: 'row-actions' }, [
        h(
          NButton,
          { size: 'small', type: 'primary', onClick: () => openItemForm(row) },
          { default: () => '编辑', icon: () => h(NIcon, null, { default: () => h(CreateOutline) }) },
        ),
        h(
          NDropdown,
          {
            trigger: 'click',
            options: dropdownOptions,
            onSelect: (key: string) => {
              if (key === 'toggle-status') toggleItem(row)
              if (key === 'delete') confirmDeleteItem(row)
            },
          },
          {
            default: () =>
              h(
                NButton,
                { size: 'small', quaternary: true },
                {
                  default: () => '更多',
                  icon: () => h(NIcon, null, { default: () => h(ArrowDownOutline) }),
                },
              ),
          },
        ),
      ])
    },
  },
]

const renderItemEmpty = () =>
  h('div', { class: 'item-empty' }, [
    h('div', { class: 'item-empty-title' }, '当前活动还没有秒杀商品'),
    h('div', { class: 'item-empty-desc' }, '点击右上角「加入商品」，从商品库存中选择 SKU 加入到这个活动。'),
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

const resetFilters = () => {
  query.enabled = null
  query.page = 1
  load()
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
  message.success(row.enabled === 1 ? '活动已禁用' : '活动已启用')
  load()
}

const confirmDeleteActivity = (row: SeckillActivity) => {
  dialog.warning({
    title: '确认删除',
    content: `确认删除活动「${row.name}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => removeActivity(row.id),
  })
}

const removeActivity = async (id: EntityId) => {
  await deleteActivity(id)
  message.success('活动已删除')
  load()
}

const loadItems = async () => {
  if (!currentActivity.value) return
  itemLoading.value = true
  try {
    items.value = await listItems(currentActivity.value.id)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '秒杀商品加载失败')
  } finally {
    itemLoading.value = false
  }
}

const openItemDrawer = async (activity: SeckillActivity) => {
  currentActivity.value = activity
  itemDrawer.value = true
  await loadItems()
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
    await loadItems()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '秒杀商品保存失败')
  } finally {
    itemSaving.value = false
  }
}

const toggleItem = async (item: SeckillItem) => {
  if (!currentActivity.value) return
  await updateItemStatus(item.id, item.status === 1 ? 0 : 1)
  message.success(item.status === 1 ? '秒杀商品已下架' : '秒杀商品已上架')
  await loadItems()
}

const confirmDeleteItem = (row: SeckillItem) => {
  dialog.warning({
    title: '确认移出',
    content: '确认将该商品移出当前活动？',
    positiveText: '移出',
    negativeText: '取消',
    onPositiveClick: () => removeItem(row.id),
  })
}

const removeItem = async (id: EntityId) => {
  if (!currentActivity.value) return
  await deleteItem(id)
  message.success('秒杀商品已移出')
  await loadItems()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <!-- 页面标题 -->
    <header class="page-head">
      <div class="page-head-text">
        <h1 class="page-title">秒杀管理</h1>
        <p class="page-subtitle">维护秒杀活动、活动商品、秒杀库存和上下架状态。</p>
      </div>
      <div class="page-head-actions">
        <n-button type="primary" @click="openActivityDrawer()">
          <template #icon>
            <n-icon><AddOutline /></n-icon>
          </template>
          新建活动
        </n-button>
      </div>
    </header>

    <!-- 搜索区 -->
    <section class="panel search-panel">
      <div class="search-row search-row--filters">
        <n-select
          v-model:value="query.enabled"
          clearable
          :options="statusOptions"
          placeholder="全部状态"
          class="filter-select"
        />
        <div class="search-row-actions">
          <n-button type="primary" @click="query.page = 1; load()">查询</n-button>
          <n-button @click="resetFilters">重置</n-button>
        </div>
      </div>
    </section>

    <!-- 活动表格 -->
    <section class="panel">
      <n-data-table
        :loading="loading"
        :columns="columns"
        :data="rows"
        :bordered="false"
        :single-line="false"
        :row-key="(row: SeckillActivity) => row.id"
        :scroll-x="900"
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

    <!-- 活动编辑 Drawer -->
    <n-drawer v-model:show="activityDrawer" :width="560" :auto-focus="false">
      <n-drawer-content
        :title="editingActivityId ? '编辑秒杀活动' : '新建秒杀活动'"
        :native-scrollbar="false"
        closable
      >
        <div class="drawer-form">
          <n-form label-placement="top">
            <div class="form-section">
              <div class="form-section-title">基础信息</div>
              <n-form-item label="活动名称" :required="true">
                <n-input v-model:value="activityForm.name" placeholder="例如：8.18 全场秒杀" />
              </n-form-item>
              <n-form-item label="活动时间" :required="true">
                <n-date-picker
                  v-model:value="activityTime"
                  type="datetimerange"
                  clearable
                  style="width: 100%"
                />
              </n-form-item>
            </div>

            <div class="form-section">
              <div class="form-section-title">活动状态</div>
              <n-form-item label="状态">
                <n-select v-model:value="activityForm.enabled" :options="statusOptions" />
              </n-form-item>
            </div>

            <div class="form-section">
              <div class="form-section-title">备注</div>
              <n-form-item label="活动说明">
                <n-input
                  v-model:value="activityForm.remark"
                  type="textarea"
                  :autosize="{ minRows: 3, maxRows: 6 }"
                  placeholder="可填写活动规则、目标、注意事项等"
                />
              </n-form-item>
            </div>
          </n-form>
        </div>

        <template #footer>
          <div class="drawer-footer">
            <n-button @click="activityDrawer = false">取消</n-button>
            <n-button type="primary" :loading="activitySaving" @click="saveActivity">
              {{ editingActivityId ? '保存修改' : '保存活动' }}
            </n-button>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>

    <!-- 活动商品管理 Drawer -->
    <n-drawer v-model:show="itemDrawer" :width="960" :auto-focus="false">
      <n-drawer-content
        :title="`活动商品 - ${currentActivity?.name || ''}`"
        :native-scrollbar="false"
        closable
      >
        <div class="items-toolbar">
          <div class="items-toolbar-text">
            管理该活动下的秒杀商品，包括加入新商品、调整秒杀价 / 库存、上下架与移出。
          </div>
          <n-button type="primary" @click="openItemForm()">
            <template #icon>
              <n-icon><AddOutline /></n-icon>
            </template>
            加入商品
          </n-button>
        </div>
        <n-data-table
          :loading="itemLoading"
          :columns="itemColumns"
          :data="items"
          :bordered="false"
          :single-line="false"
          :render-empty="renderItemEmpty"
          :scroll-x="920"
        />
      </n-drawer-content>
    </n-drawer>

    <!-- 秒杀商品编辑 Drawer -->
    <n-drawer v-model:show="itemFormDrawer" :width="560" :auto-focus="false">
      <n-drawer-content
        :title="editingItemId ? '编辑秒杀商品' : '加入秒杀商品'"
        :native-scrollbar="false"
        closable
      >
        <div class="drawer-form">
          <n-form label-placement="top">
            <div class="form-section">
              <div class="form-section-title">选择商品</div>
              <n-form-item label="从商品库存选择商品" :required="true">
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
              <n-form-item label="选择 SKU" :required="true">
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
            </div>

            <div class="form-section">
              <div class="form-section-title">价格与库存</div>
              <n-grid :cols="2" :x-gap="16">
                <n-form-item-gi label="秒杀价" :required="true">
                  <n-input-number
                    v-model:value="itemForm.seckillPrice"
                    :min="0.01"
                    :precision="2"
                    style="width: 100%"
                  >
                    <template #prefix>¥</template>
                  </n-input-number>
                </n-form-item-gi>
                <n-form-item-gi label="秒杀库存" :required="true">
                  <n-input-number
                    v-model:value="itemForm.seckillStock"
                    :min="0"
                    :max="selectedSkuInventory"
                    style="width: 100%"
                  />
                </n-form-item-gi>
                <n-form-item-gi label="限购数量">
                  <n-input-number v-model:value="itemForm.limitPerUser" :min="1" style="width: 100%" />
                </n-form-item-gi>
                <n-form-item-gi label="排序">
                  <n-input-number v-model:value="itemForm.sortOrder" :min="0" style="width: 100%" />
                </n-form-item-gi>
              </n-grid>
            </div>

            <div class="form-section">
              <div class="form-section-title">状态</div>
              <n-form-item label="上下架状态">
                <n-select v-model:value="itemForm.status" :options="itemStatusOptions" />
              </n-form-item>
            </div>
          </n-form>
        </div>

        <template #footer>
          <div class="drawer-footer">
            <n-button @click="itemFormDrawer = false">取消</n-button>
            <n-button type="primary" :loading="itemSaving" @click="saveItem">
              {{ editingItemId ? '保存修改' : '加入活动' }}
            </n-button>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<style scoped>
/* —— 搜索区 —— */
.search-panel {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
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

.filter-select {
  width: 200px;
}

.search-row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

/* —— 活动列 —— */
.activity-cell,
.item-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.activity-name,
.item-name {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: var(--font-weight-medium);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-id,
.item-sku {
  font-family: var(--font-family-mono);
  color: var(--color-text-tertiary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-text {
  color: var(--color-text-secondary);
  font-size: 13px;
}

/* —— 价格列 —— */
.price-cell {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  color: var(--color-text-primary);
}

.price-cell--muted {
  color: var(--color-text-tertiary);
}

.price-symbol {
  font-size: 12px;
  font-weight: var(--font-weight-regular);
}

.price-amount {
  font-size: 15px;
  font-weight: var(--font-weight-semibold);
}

.price-amount--hot {
  color: var(--color-primary);
}

/* —— 库存列 —— */
.count-cell {
  font-size: 15px;
  font-weight: var(--font-weight-semibold);
}

.count-hot {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.count-muted {
  color: var(--color-text-tertiary);
}

.stock-normal {
  color: var(--color-text-primary);
}

.stock-warning {
  color: var(--color-warning);
}

.stock-danger {
  color: var(--color-danger);
}

/* —— 操作区 —— */
.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

:deep(.dropdown-danger-item) {
  color: var(--color-danger);
}

/* —— Drawer —— */
.drawer-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-section {
  padding: 16px 0;
  border-top: 1px solid var(--color-border);
}

.form-section:first-child {
  border-top: none;
  padding-top: 4px;
}

.form-section-title {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: var(--font-weight-semibold);
  margin-bottom: 12px;
  letter-spacing: 0.2px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* —— 库存提示 —— */
.stock-hint {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: -4px 0 4px;
}

.stock-hint span {
  padding: 4px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-tag);
  color: var(--color-text-secondary);
  background: var(--color-surface-subtle);
  font-size: 12px;
}

/* —— 活动商品 Drawer 工具栏 —— */
.items-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--color-surface-subtle);
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border);
}

.items-toolbar-text {
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

/* —— 空状态 —— */
.item-empty {
  padding: 56px 16px;
  text-align: center;
}

.item-empty-title {
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: var(--font-weight-semibold);
}

.item-empty-desc {
  margin-top: 6px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

/* —— 分页 —— */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px 16px;
}

/* —— Responsive —— */
@media (max-width: 640px) {
  .search-row--filters {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-select {
    width: 100%;
  }

  .search-row-actions {
    margin-left: 0;
  }

  .items-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
