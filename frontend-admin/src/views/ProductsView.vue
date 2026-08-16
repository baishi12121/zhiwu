<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns, DropdownOption, UploadCustomRequestOptions } from 'naive-ui'
import { NButton, NDropdown, NIcon, NImage, useDialog, useMessage } from 'naive-ui'
import {
  AddOutline,
  ArrowDownOutline,
  CashOutline,
  CubeOutline,
  ImagesOutline,
  PricetagsOutline,
  SearchOutline,
  TrashOutline,
} from '@vicons/ionicons5'
import StatusTag from '@/components/StatusTag.vue'
import {
  addSku,
  createProduct,
  deleteProduct,
  deleteSku,
  getProduct,
  listProducts,
  listSkus,
  updateProduct,
  updateProductStatus,
  updateSku,
  updateSkuStatus,
  uploadFile,
} from '@/api/products'
import { listCategories } from '@/api/categories'
import type {
  AdminCategory,
  AdminProduct,
  AdminProductImage,
  AdminProductProperty,
  AdminProductSku,
  EntityId,
  ProductSaveRequest,
} from '@/types/admin'

type ProductForm = ProductSaveRequest & {
  skus: Array<Partial<AdminProductSku>>
  images: AdminProductImage[]
  properties: AdminProductProperty[]
}

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const rows = ref<AdminProduct[]>([])
const categories = ref<AdminCategory[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  pageSize: 10,
  keyword: '',
  categoryId: null as EntityId | null,
  status: null as number | null,
})

const productDrawer = ref(false)
const productSaving = ref(false)
const editingProductId = ref<EntityId | null>(null)
const uploadingKeys = reactive(new Set<string>())
const productForm = reactive<ProductForm>({
  categoryId: undefined,
  brandId: undefined,
  spuCode: '',
  name: '',
  subtitle: '',
  description: '',
  price: undefined,
  oldPrice: undefined,
  discount: undefined,
  inventory: 0,
  status: 1,
  isPreSale: 0,
  skus: [],
  images: [],
  properties: [],
})

const skuDrawer = ref(false)
const skuFormDrawer = ref(false)
const skuSaving = ref(false)
const currentProduct = ref<AdminProduct | null>(null)
const skus = ref<AdminProductSku[]>([])
const editingSkuId = ref<EntityId | null>(null)
const skuForm = reactive<Partial<AdminProductSku>>({
  skuCode: '',
  price: 0,
  oldPrice: undefined,
  inventory: 0,
  picture: '',
  status: 1,
})

const categoryOptions = computed(() =>
  categories.value.map((item) => ({
    label: item.name,
    value: item.id,
  })),
)
const categoryMap = computed(() => new Map(categories.value.map((item) => [item.id, item.name])))
const statusOptions = [
  { label: '上架', value: 1 },
  { label: '下架', value: 0 },
]

const productCoverUrl = (row: AdminProduct) => {
  const images = row.images || []
  const mainImage = images.find((image) => image.imageType === 1 && image.imageUrl)
  return mainImage?.imageUrl || images.find((image) => image.imageUrl)?.imageUrl || ''
}

// —— 主图 / 副图 分离计算 ——
const mainImage = computed(() => productForm.images.find((img) => img.imageType === 1 && img.imageUrl) || null)
const subImages = computed(() => productForm.images.filter((img) => img.imageType !== 1))

// 将副图设为主图：原主图降为副图，选中副图升为主图
const setSubAsMain = (index: number) => {
  const subList = productForm.images.filter((img) => img.imageType !== 1)
  const target = subList[index]
  if (!target) return
  // 原主图降为副图
  const oldMain = productForm.images.find((img) => img.imageType === 1)
  if (oldMain) oldMain.imageType = 2
  // 选中副图升为主图
  target.imageType = 1
}

const removeMainImage = () => {
  const idx = productForm.images.findIndex((img) => img.imageType === 1)
  if (idx !== -1) productForm.images.splice(idx, 1)
}

const removeSubImage = (subIndex: number) => {
  const subList = productForm.images.filter((img) => img.imageType !== 1)
  const target = subList[subIndex]
  if (!target) return
  const realIdx = productForm.images.indexOf(target)
  if (realIdx !== -1) productForm.images.splice(realIdx, 1)
}

const uploadMainImage = async (options: UploadCustomRequestOptions) => {
  const file = options.file.file
  if (!file) {
    message.error('请选择要上传的图片')
    options.onError()
    return
  }
  uploadingKeys.add('main-image')
  try {
    const result = await uploadFile(file)
    // 如果已有主图，替换其 URL；否则新增 type=1
    const oldMain = productForm.images.find((img) => img.imageType === 1)
    if (oldMain) {
      oldMain.imageUrl = result.url
    } else {
      productForm.images.unshift({
        imageType: 1,
        imageUrl: result.url,
        sortOrder: 0,
      })
    }
    message.success('主图上传成功')
    options.onFinish()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '图片上传失败')
    options.onError()
  } finally {
    uploadingKeys.delete('main-image')
  }
}

const addSubImageFromUpload = (url: string) => {
  productForm.images.push({
    imageType: 2,
    imageUrl: url,
    sortOrder: productForm.images.filter((img) => img.imageType !== 1).length,
  })
}

const uploadSubImage = async (options: UploadCustomRequestOptions) => {
  const file = options.file.file
  if (!file) {
    message.error('请选择要上传的图片')
    options.onError()
    return
  }
  uploadingKeys.add('sub-image')
  try {
    const result = await uploadFile(file)
    addSubImageFromUpload(result.url)
    message.success('副图上传成功')
    options.onFinish()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '图片上传失败')
    options.onError()
  } finally {
    uploadingKeys.delete('sub-image')
  }
}

// 库存状态判断
const inventoryStatus = (value?: number) => {
  const v = Number(value || 0)
  if (v === 0) return { type: 'danger' as const, text: '缺货' }
  if (v < 10) return { type: 'warning' as const, text: '偏低' }
  return { type: 'default' as const, text: '' }
}

// —— 更多操作 Dropdown ——
const buildRowDropdownOptions = (row: AdminProduct): DropdownOption[] => [
  {
    label: row.status === 1 ? '下架商品' : '上架商品',
    key: 'toggle-status',
    icon: () => h(NIcon, null, { default: () => h(PricetagsOutline) }),
  },
  { type: 'divider', key: 'd1' },
  {
    label: '删除商品',
    key: 'delete',
    icon: () => h(NIcon, { color: '#D03050' }, { default: () => h(TrashOutline) }),
  },
]

const handleRowDropdown = (key: string, row: AdminProduct) => {
  if (key === 'toggle-status') {
    toggleStatus(row)
  } else if (key === 'delete') {
    confirmDeleteProduct(row)
  }
}

const confirmDeleteProduct = (row: AdminProduct) => {
  dialog.warning({
    title: '确认删除',
    content: `确认删除商品「${row.name}」？此操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => removeProduct(row.id),
  })
}

const confirmDeleteSku = (row: AdminProductSku) => {
  dialog.warning({
    title: '确认删除',
    content: `确认删除 SKU「${row.skuCode || row.id}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => removeSku(row.id),
  })
}

const columns = computed<DataTableColumns<AdminProduct>>(() => [
  {
    title: '图片',
    key: 'cover',
    width: 96,
    render(row) {
      const coverUrl = productCoverUrl(row)
      return coverUrl
        ? h(NImage, {
            src: coverUrl,
            width: 72,
            height: 72,
            objectFit: 'cover',
            class: 'product-thumb',
            previewDisabled: true,
          })
        : h('div', { class: 'product-thumb product-thumb--empty' }, [
            h(NIcon, { size: 22, color: '#98A2B3' }, { default: () => h(ImagesOutline) }),
          ])
    },
  },
  {
    title: '商品',
    key: 'name',
    minWidth: 240,
    render(row) {
      const categoryName = categoryMap.value.get(row.categoryId)
      return h('div', { class: 'product-meta' }, [
        h('strong', { class: 'product-name' }, row.name),
        h('div', { class: 'product-sub' }, [
          row.spuCode ? h('span', { class: 'product-spu' }, `SPU ${row.spuCode}`) : null,
          categoryName ? h('span', { class: 'product-cat' }, categoryName) : null,
        ].filter(Boolean)),
      ])
    },
  },
  {
    title: '售价',
    key: 'price',
    width: 130,
    render: (row) =>
      h('div', { class: 'price-cell' }, [
        h('span', { class: 'price-symbol' }, '¥'),
        h('span', { class: 'price-value' }, Number(row.price || 0).toFixed(2)),
      ]),
  },
  {
    title: '库存',
    key: 'inventory',
    width: 110,
    render(row) {
      const status = inventoryStatus(row.inventory)
      return h('div', { class: 'num-cell' }, [
        h('span', { class: `num-value num-value--${status.type}` }, String(row.inventory ?? 0)),
        status.text ? h('span', { class: `num-hint num-hint--${status.type}` }, status.text) : null,
      ])
    },
  },
  {
    title: '销量',
    key: 'salesCount',
    width: 100,
    render: (row) => h('span', { class: 'num-value num-value--default' }, String(row.salesCount ?? 0)),
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) => h(StatusTag, { value: row.status, activeText: '上架', inactiveText: '下架' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    fixed: 'right',
    render(row) {
      return h('div', { class: 'row-actions' }, [
        h(
          NButton,
          { size: 'small', type: 'primary', onClick: () => openProductDrawer(row.id) },
          { default: () => '编辑' },
        ),
        h(
          NButton,
          { size: 'small', onClick: () => openSkuDrawer(row) },
          { default: () => 'SKU' },
        ),
        h(
          NDropdown,
          {
            trigger: 'click',
            options: buildRowDropdownOptions(row),
            onSelect: (key: string) => handleRowDropdown(key, row),
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
])

const skuColumns: DataTableColumns<AdminProductSku> = [
  { title: 'SKU 编码', key: 'skuCode', minWidth: 160, render: (row) => row.skuCode || `SKU ${row.id}` },
  {
    title: '售价',
    key: 'price',
    width: 130,
    render: (row) =>
      h('div', { class: 'price-cell' }, [
        h('span', { class: 'price-symbol' }, '¥'),
        h('span', { class: 'price-value' }, Number(row.price || 0).toFixed(2)),
      ]),
  },
  {
    title: '库存',
    key: 'inventory',
    width: 110,
    render: (row) => {
      const status = inventoryStatus(row.inventory)
      return h('div', { class: 'num-cell' }, [
        h('span', { class: `num-value num-value--${status.type}` }, String(row.inventory ?? 0)),
        status.text ? h('span', { class: `num-hint num-hint--${status.type}` }, status.text) : null,
      ])
    },
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) => h(StatusTag, { value: row.status, activeText: '上架', inactiveText: '下架' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render(row) {
      return h('div', { class: 'row-actions' }, [
        h(NButton, { size: 'small', onClick: () => openSkuForm(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', onClick: () => toggleSkuStatus(row) }, {
          default: () => (row.status === 1 ? '下架' : '上架'),
        }),
        h(NButton, {
          size: 'small',
          quaternary: true,
          type: 'error',
          onClick: () => confirmDeleteSku(row),
        }, { default: () => '删除' }),
      ])
    },
  },
]

const productRowKey = (row: AdminProduct) => row.id

const load = async () => {
  loading.value = true
  try {
    const page = await listProducts(query)
    rows.value = page.items
    total.value = page.total
    query.page = page.page
    query.pageSize = page.pageSize
  } catch (error) {
    message.error(error instanceof Error ? error.message : '商品列表加载失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  query.keyword = ''
  query.categoryId = null
  query.status = null
  query.page = 1
  load()
}

const createBlankProduct = (): ProductForm => ({
  categoryId: undefined,
  brandId: undefined,
  spuCode: '',
  name: '',
  subtitle: '',
  description: '',
  price: undefined,
  oldPrice: undefined,
  discount: undefined,
  inventory: 0,
  status: 1,
  isPreSale: 0,
  skus: [],
  images: [],
  properties: [],
})

const openProductDrawer = async (id?: EntityId) => {
  editingProductId.value = id || null
  Object.assign(productForm, createBlankProduct())
  if (id) {
    try {
      const detail = await getProduct(id)
      Object.assign(productForm, {
        categoryId: detail.categoryId,
        brandId: detail.brandId,
        spuCode: detail.spuCode,
        name: detail.name,
        subtitle: detail.subtitle,
        description: detail.description,
        price: detail.price,
        oldPrice: detail.oldPrice,
        discount: detail.discount,
        inventory: detail.inventory,
        status: detail.status,
        isPreSale: detail.isPreSale,
        skus: detail.skus || [],
        images: detail.images || [],
        properties: detail.properties || [],
      })
    } catch (error) {
      message.error(error instanceof Error ? error.message : '商品详情加载失败')
      return
    }
  }
  productDrawer.value = true
}

const cleanProductPayload = () => ({
  ...productForm,
  skus: productForm.skus.filter((item) => item.price),
  images: productForm.images.filter((item) => item.imageUrl),
  properties: productForm.properties.filter((item) => item.name && item.value),
})

const saveProduct = async () => {
  if (!productForm.categoryId || !productForm.name || !productForm.price) {
    message.warning('请填写分类、商品名称和售价')
    return
  }
  productSaving.value = true
  try {
    if (editingProductId.value) {
      await updateProduct(editingProductId.value, cleanProductPayload())
      message.success('商品已更新')
    } else {
      await createProduct(cleanProductPayload())
      message.success('商品已创建')
    }
    productDrawer.value = false
    load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    productSaving.value = false
  }
}

const toggleStatus = async (row: AdminProduct) => {
  await updateProductStatus(row.id, row.status === 1 ? 0 : 1)
  message.success(row.status === 1 ? '商品已下架' : '商品已上架')
  load()
}

const removeProduct = async (id: EntityId) => {
  await deleteProduct(id)
  message.success('商品已删除')
  load()
}

const openSkuDrawer = async (row: AdminProduct) => {
  currentProduct.value = row
  skuDrawer.value = true
  try {
    skus.value = await listSkus(row.id)
  } catch (error) {
    message.error(error instanceof Error ? error.message : 'SKU 列表加载失败')
  }
}

const openSkuForm = (sku?: AdminProductSku) => {
  editingSkuId.value = sku?.id || null
  Object.assign(skuForm, {
    skuCode: sku?.skuCode || '',
    price: sku?.price || 0,
    oldPrice: sku?.oldPrice,
    inventory: sku?.inventory || 0,
    picture: sku?.picture || '',
    status: sku?.status ?? 1,
  })
  skuFormDrawer.value = true
}

const saveSku = async () => {
  if (!currentProduct.value || !skuForm.price) {
    message.warning('请填写 SKU 售价')
    return
  }
  skuSaving.value = true
  try {
    if (editingSkuId.value) {
      await updateSku(editingSkuId.value, skuForm)
      message.success('SKU 已更新')
    } else {
      await addSku(currentProduct.value.id, skuForm)
      message.success('SKU 已新增')
    }
    skuFormDrawer.value = false
    skus.value = await listSkus(currentProduct.value.id)
  } catch (error) {
    message.error(error instanceof Error ? error.message : 'SKU 保存失败')
  } finally {
    skuSaving.value = false
  }
}

const toggleSkuStatus = async (sku: AdminProductSku) => {
  if (!currentProduct.value) return
  await updateSkuStatus(sku.id, sku.status === 1 ? 0 : 1)
  message.success('SKU 状态已更新')
  skus.value = await listSkus(currentProduct.value.id)
}

const removeSku = async (id: EntityId) => {
  if (!currentProduct.value) return
  await deleteSku(id)
  message.success('SKU 已删除')
  skus.value = await listSkus(currentProduct.value.id)
}

onMounted(async () => {
  categories.value = await listCategories().catch(() => [])
  load()
})
</script>

<template>
  <div class="page">
    <!-- 页面标题 -->
    <header class="page-head">
      <div class="page-head-text">
        <h1 class="page-title">商品管理</h1>
        <p class="page-subtitle">管理商品、SKU、分类图片和上下架状态。</p>
      </div>
      <div class="page-head-actions">
        <n-button type="primary" @click="openProductDrawer()">
          <template #icon>
            <n-icon><AddOutline /></n-icon>
          </template>
          新建商品
        </n-button>
      </div>
    </header>

    <!-- 搜索区 -->
    <section class="panel search-panel">
      <div class="search-row search-row--main">
        <n-input
          v-model:value="query.keyword"
          clearable
          placeholder="搜索商品名称 / SPU / 编码"
          class="search-input"
        >
          <template #prefix>
            <n-icon :color="'#98A2B3'"><SearchOutline /></n-icon>
          </template>
        </n-input>
      </div>
      <div class="search-row search-row--filters">
        <n-select
          v-model:value="query.categoryId"
          clearable
          :options="categoryOptions"
          placeholder="全部分类"
          class="filter-select"
        />
        <n-select
          v-model:value="query.status"
          clearable
          :options="statusOptions"
          placeholder="全部状态"
          class="filter-select filter-select--sm"
        />
        <div class="search-row-actions">
          <n-button type="primary" @click="query.page = 1; load()">查询</n-button>
          <n-button @click="resetFilters">重置</n-button>
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
        :row-key="productRowKey"
        :scroll-x="1000"
        remote
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

    <!-- 新建/编辑 商品 Drawer -->
    <n-drawer v-model:show="productDrawer" :width="640" :auto-focus="false">
      <n-drawer-content
        :title="editingProductId ? '编辑商品' : '新建商品'"
        :native-scrollbar="false"
        closable
      >
        <template #header-extra>
          <span class="drawer-header-extra">
            {{ editingProductId ? '修改基础信息、价格与库存' : '填写基础信息、价格和库存' }}
          </span>
        </template>

        <div class="drawer-form">
          <!-- 基础信息 -->
          <section class="form-section">
            <h3 class="form-section-title">
              <n-icon :size="16" color="#18A058"><CubeOutline /></n-icon>
              基础信息
            </h3>
            <n-form label-placement="top" :show-require-mark="false">
              <n-grid :cols="2" :x-gap="16" :y-gap="0">
                <n-form-item-gi label="商品名称" :required="true">
                  <n-input v-model:value="productForm.name" placeholder="输入商品名称" />
                </n-form-item-gi>
                <n-form-item-gi label="分类" :required="true">
                  <n-select v-model:value="productForm.categoryId" :options="categoryOptions" placeholder="选择分类" />
                </n-form-item-gi>
                <n-form-item-gi label="SPU 编码">
                  <n-input v-model:value="productForm.spuCode" placeholder="可选" />
                </n-form-item-gi>
                <n-form-item-gi label="品牌 ID">
                  <n-input-number v-model:value="productForm.brandId" placeholder="可选" :show-button="false" style="width: 100%" />
                </n-form-item-gi>
              </n-grid>
            </n-form>
          </section>

          <!-- 价格与库存 -->
          <section class="form-section">
            <h3 class="form-section-title">
              <n-icon :size="16" color="#18A058"><CashOutline /></n-icon>
              价格与库存
            </h3>
            <n-form label-placement="top">
              <n-grid :cols="2" :x-gap="16">
                <n-form-item-gi label="售价" :required="true">
                  <n-input-number v-model:value="productForm.price" :min="0.01" :show-button="false" style="width: 100%" placeholder="0.00">
                    <template #prefix>¥</template>
                  </n-input-number>
                </n-form-item-gi>
                <n-form-item-gi label="原价">
                  <n-input-number v-model:value="productForm.oldPrice" :min="0" :show-button="false" style="width: 100%" placeholder="0.00">
                    <template #prefix>¥</template>
                  </n-input-number>
                </n-form-item-gi>
                <n-form-item-gi label="库存">
                  <n-input-number v-model:value="productForm.inventory" :min="0" style="width: 100%" />
                </n-form-item-gi>
                <n-form-item-gi label="状态">
                  <n-select v-model:value="productForm.status" :options="statusOptions" />
                </n-form-item-gi>
              </n-grid>
            </n-form>
          </section>

          <!-- 商品描述 -->
          <section class="form-section">
            <h3 class="form-section-title">
              <n-icon :size="16" color="#18A058"><PricetagsOutline /></n-icon>
              商品描述
            </h3>
            <n-form label-placement="top">
              <n-form-item label="副标题">
                <n-input v-model:value="productForm.subtitle" placeholder="一句话卖点" />
              </n-form-item>
              <n-form-item label="商品描述">
                <n-input
                  v-model:value="productForm.description"
                  type="textarea"
                  :autosize="{ minRows: 3, maxRows: 8 }"
                  placeholder="详细描述商品特性、规格、适用场景"
                />
              </n-form-item>
            </n-form>
          </section>

          <!-- 商品图片 -->
          <section class="form-section">
            <h3 class="form-section-title">
              <n-icon :size="16" color="#18A058"><ImagesOutline /></n-icon>
              商品图片
            </h3>

            <!-- 主图 -->
            <div class="image-main-area">
              <div class="image-main-label">主图</div>
              <div class="image-main-row">
                <div class="image-main-preview">
                  <n-image
                    v-if="mainImage?.imageUrl"
                    :src="mainImage.imageUrl"
                    width="160"
                    height="160"
                    object-fit="cover"
                    preview-disabled
                  />
                  <div v-else class="image-main-empty">
                    <n-icon :size="32" color="#C5C9D1"><ImagesOutline /></n-icon>
                  </div>
                </div>
                <div class="image-main-actions">
                  <n-upload
                    accept="image/*"
                    :show-file-list="false"
                    :custom-request="uploadMainImage"
                  >
                    <n-button :loading="uploadingKeys.has('main-image')">
                      <template #icon><n-icon><ImagesOutline /></n-icon></template>
                      {{ mainImage ? '更换主图' : '上传主图' }}
                    </n-button>
                  </n-upload>
                  <n-button v-if="mainImage" quaternary type="error" size="small" @click="removeMainImage">
                    移除
                  </n-button>
                </div>
              </div>
            </div>

            <!-- 副图 -->
            <div class="image-sub-area">
              <div class="image-sub-label">副图（可设一张为主图）</div>
              <div class="image-sub-grid">
                <div
                  v-for="(img, idx) in subImages"
                  :key="`sub-${idx}`"
                  class="image-sub-card"
                >
                  <div class="image-sub-preview">
                    <n-image
                      v-if="img.imageUrl"
                      :src="img.imageUrl"
                      width="140"
                      height="140"
                      object-fit="cover"
                      preview-disabled
                    />
                    <div v-else class="image-sub-empty">
                      <n-icon :size="24" color="#C5C9D1"><ImagesOutline /></n-icon>
                    </div>
                  </div>
                  <div class="image-sub-controls">
                    <n-button size="small" type="primary" quaternary @click="setSubAsMain(idx)">
                      设为主图
                    </n-button>
                    <n-button size="small" quaternary type="error" @click="removeSubImage(idx)">
                      移除
                    </n-button>
                  </div>
                </div>

                <n-upload
                  accept="image/*"
                  :show-file-list="false"
                  :custom-request="uploadSubImage"
                  class="image-sub-add-trigger"
                >
                  <div class="image-sub-add" :class="{ 'image-add--loading': uploadingKeys.has('sub-image') }">
                    <n-icon :size="24" color="#98A2B3"><AddOutline /></n-icon>
                    <span>添加副图</span>
                  </div>
                </n-upload>
              </div>
            </div>
          </section>

          <!-- 商品属性 -->
          <section class="form-section">
            <h3 class="form-section-title">
              <n-icon :size="16" color="#18A058"><PricetagsOutline /></n-icon>
              商品属性
            </h3>
            <p class="form-section-desc">颜色、尺码、材质等可动态添加。</p>
            <div class="stack">
              <div v-for="(prop, index) in productForm.properties" :key="`prop-${index}`" class="inline-row">
                <n-input v-model:value="prop.name" placeholder="属性名（如 颜色）" />
                <n-input v-model:value="prop.value" placeholder="属性值（如 黑色）" />
                <n-input-number
                  v-model:value="prop.sortOrder"
                  placeholder="排序"
                  :show-button="false"
                  style="width: 110px"
                />
                <n-button quaternary type="error" @click="productForm.properties.splice(index, 1)">移除</n-button>
              </div>
              <n-button
                dashed
                block
                @click="productForm.properties.push({ name: '', value: '', sortOrder: productForm.properties.length })"
              >
                + 添加属性
              </n-button>
            </div>
          </section>

          <!-- 初始 SKU（仅新建） -->
          <section v-if="!editingProductId" class="form-section">
            <h3 class="form-section-title">
              <n-icon :size="16" color="#18A058"><CubeOutline /></n-icon>
              初始 SKU
            </h3>
            <p class="form-section-desc">创建后可在 SKU 管理中继续维护。</p>
            <div class="stack">
              <div v-for="(sku, index) in productForm.skus" :key="`sku-${index}`" class="inline-row">
                <n-input v-model:value="sku.skuCode" placeholder="SKU 编码" />
                <n-input-number v-model:value="sku.price" :min="0.01" placeholder="售价" :show-button="false" style="width: 140px">
                  <template #prefix>¥</template>
                </n-input-number>
                <n-input-number v-model:value="sku.inventory" :min="0" placeholder="库存" style="width: 120px" />
                <n-button quaternary type="error" @click="productForm.skus.splice(index, 1)">移除</n-button>
              </div>
              <n-button dashed block @click="productForm.skus.push({ skuCode: '', price: 0, inventory: 0, status: 1 })">
                + 添加 SKU
              </n-button>
            </div>
          </section>
        </div>

        <template #footer>
          <div class="drawer-footer">
            <n-button @click="productDrawer = false">取消</n-button>
            <n-button type="primary" :loading="productSaving" @click="saveProduct">
              {{ editingProductId ? '保存修改' : '保存商品' }}
            </n-button>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>

    <!-- SKU 管理 Drawer -->
    <n-drawer v-model:show="skuDrawer" :width="780" :auto-focus="false">
      <n-drawer-content
        :title="`SKU 管理`"
        :native-scrollbar="false"
        closable
      >
        <template #header-extra>
          <span class="drawer-header-extra">{{ currentProduct?.name || '' }}</span>
        </template>

        <div class="sku-toolbar">
          <p class="sku-hint">为商品维护不同规格的 SKU、价格与库存。</p>
          <n-button type="primary" size="small" @click="openSkuForm()">
            <template #icon><n-icon><AddOutline /></n-icon></template>
            新增 SKU
          </n-button>
        </div>

        <n-data-table
          :columns="skuColumns"
          :data="skus"
          :bordered="false"
          :row-key="(row: AdminProductSku) => row.id"
        />
      </n-drawer-content>
    </n-drawer>

    <!-- SKU 编辑 Drawer -->
    <n-drawer v-model:show="skuFormDrawer" :width="460" :auto-focus="false">
      <n-drawer-content
        :title="editingSkuId ? '编辑 SKU' : '新增 SKU'"
        :native-scrollbar="false"
        closable
      >
        <div class="drawer-form">
          <n-form label-placement="top">
            <n-form-item label="SKU 编码">
              <n-input v-model:value="skuForm.skuCode" placeholder="SKU 唯一编码" />
            </n-form-item>
            <n-grid :cols="2" :x-gap="16">
              <n-form-item-gi label="售价">
                <n-input-number v-model:value="skuForm.price" :min="0.01" :show-button="false" style="width: 100%">
                  <template #prefix>¥</template>
                </n-input-number>
              </n-form-item-gi>
              <n-form-item-gi label="原价">
                <n-input-number v-model:value="skuForm.oldPrice" :min="0" :show-button="false" style="width: 100%">
                  <template #prefix>¥</template>
                </n-input-number>
              </n-form-item-gi>
              <n-form-item-gi label="库存">
                <n-input-number v-model:value="skuForm.inventory" :min="0" style="width: 100%" />
              </n-form-item-gi>
              <n-form-item-gi label="状态">
                <n-select v-model:value="skuForm.status" :options="statusOptions" />
              </n-form-item-gi>
            </n-grid>
            <n-form-item label="图片 URL">
              <n-input v-model:value="skuForm.picture" placeholder="可选" />
            </n-form-item>
          </n-form>
        </div>
        <template #footer>
          <div class="drawer-footer">
            <n-button @click="skuFormDrawer = false">取消</n-button>
            <n-button type="primary" :loading="skuSaving" @click="saveSku">保存</n-button>
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
}

.search-row--main {
  width: 100%;
}

.search-input {
  width: 100%;
  max-width: 480px;
}

.search-row--filters {
  flex-wrap: wrap;
}

.filter-select {
  width: 200px;
}

.filter-select--sm {
  width: 150px;
}

.search-row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

/* —— 商品缩略图列 —— */
.product-thumb {
  width: 72px !important;
  min-width: 72px;
  max-width: 72px;
  height: 72px !important;
  max-height: 72px;
  border-radius: var(--radius-image);
  overflow: hidden;
  flex-shrink: 0;
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  display: grid;
  place-items: center;
}

.product-thumb--empty {
  background: var(--color-surface-subtle);
}

/* —— 商品信息列 —— */
.product-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.product-name {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: var(--font-weight-medium);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.product-spu {
  font-family: var(--font-family-mono);
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.product-cat {
  font-size: 12px;
  color: var(--color-text-secondary);
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--color-surface-subtle);
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

/* —— 库存/销量 —— */
.num-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
}

.num-value {
  font-size: 15px;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  line-height: 1.2;
}

.num-value--warning {
  color: var(--color-warning);
}

.num-value--danger {
  color: var(--color-danger);
}

.num-value--default {
  color: var(--color-text-primary);
}

.num-hint {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.num-hint--warning {
  color: var(--color-warning);
}

.num-hint--danger {
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
.drawer-header-extra {
  color: var(--color-text-tertiary);
  font-size: 12px;
  margin-left: 8px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* —— 商品图片区 —— */
.image-main-area {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
}

.image-main-label,
.image-sub-label {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: var(--font-weight-medium);
}

.image-main-row {
  display: flex;
  align-items: flex-start;
  gap: 20px;
}

.image-main-preview {
  width: 160px;
  height: 160px;
  border-radius: var(--radius-card);
  overflow: hidden;
  flex-shrink: 0;
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  display: grid;
  place-items: center;
}

.image-main-empty {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
}

.image-main-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
  padding-top: 8px;
}

.image-sub-area {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.image-sub-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.image-sub-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 140px;
}

.image-sub-preview {
  width: 140px;
  height: 140px;
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.image-sub-empty {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
}

.image-sub-controls {
  display: flex;
  gap: 4px;
  justify-content: space-between;
}

.image-sub-add-trigger {
  display: block;
}

.image-sub-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 140px;
  height: 140px;
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-card);
  background: var(--color-surface-subtle);
  color: var(--color-text-tertiary);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--motion-fast);
}

.image-sub-add:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: rgba(24, 160, 88, 0.04);
}

.image-add--loading {
  pointer-events: none;
  opacity: 0.6;
}

/* —— Inline row —— */
.inline-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 110px auto;
  gap: 8px;
  align-items: center;
}

/* —— SKU drawer toolbar —— */
.sku-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 4px 16px;
  border-bottom: 1px solid var(--color-border-light);
  margin-bottom: 12px;
}

.sku-hint {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

/* —— Responsive —— */
@media (max-width: 760px) {
  .search-row--filters {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-select,
  .filter-select--sm {
    width: 100%;
  }

  .search-row-actions {
    margin-left: 0;
  }

  .image-sub-grid {
    gap: 8px;
  }

  .inline-row {
    grid-template-columns: 1fr;
  }
}
</style>
