<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns, UploadCustomRequestOptions } from 'naive-ui'
import { NButton, NPopconfirm, NSpace, useMessage } from 'naive-ui'
import { AddOutline, RefreshOutline, SearchOutline } from '@vicons/ionicons5'
import StatusTag from '@/components/StatusTag.vue'
import {
  addSku,
  createProduct,
  deleteProduct,
  deleteSku,
  getProduct,
  listCategories,
  listProducts,
  listSkus,
  updateProduct,
  updateProductStatus,
  updateSku,
  updateSkuStatus,
  uploadFile,
} from '@/api/products'
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

const money = (value?: number) =>
  new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(Number(value || 0))

const columns: DataTableColumns<AdminProduct> = [
  { title: 'ID', key: 'id', width: 80 },
  {
    title: '商品',
    key: 'name',
    minWidth: 220,
    render(row) {
      return h('div', { class: 'product-name-cell' }, [
        h('strong', row.name),
        h('span', row.subtitle || row.spuCode || '无副标题'),
      ])
    },
  },
  {
    title: '分类',
    key: 'categoryId',
    render(row) {
      return categoryMap.value.get(row.categoryId) || row.categoryId || '-'
    },
  },
  { title: '售价', key: 'price', render: (row) => money(row.price) },
  { title: '库存', key: 'inventory' },
  { title: '销量', key: 'salesCount' },
  {
    title: '状态',
    key: 'status',
    render: (row) => h(StatusTag, { value: row.status, activeText: '上架', inactiveText: '下架' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 300,
    render(row) {
      return h(NSpace, { size: 8 }, () => [
        h(NButton, { size: 'small', onClick: () => openProductDrawer(row.id) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', onClick: () => openSkuDrawer(row) }, { default: () => 'SKU' }),
        h(
          NButton,
          { size: 'small', type: row.status === 1 ? 'warning' : 'success', onClick: () => toggleStatus(row) },
          { default: () => (row.status === 1 ? '下架' : '上架') },
        ),
        h(
          NPopconfirm,
          { onPositiveClick: () => removeProduct(row.id) },
          {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
            default: () => `确认删除商品「${row.name}」？`,
          },
        ),
      ])
    },
  },
]

const skuColumns: DataTableColumns<AdminProductSku> = [
  { title: 'SKU ID', key: 'id', width: 90 },
  { title: 'SKU 编码', key: 'skuCode' },
  { title: '售价', key: 'price', render: (row) => money(row.price) },
  { title: '库存', key: 'inventory' },
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
        h(NButton, { size: 'small', onClick: () => openSkuForm(row) }, { default: () => '编辑' }),
        h(
          NButton,
          { size: 'small', type: row.status === 1 ? 'warning' : 'success', onClick: () => toggleSkuStatus(row) },
          { default: () => (row.status === 1 ? '下架' : '上架') },
        ),
        h(
          NPopconfirm,
          { onPositiveClick: () => removeSku(row.id) },
          {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
            default: () => `确认删除 SKU「${row.skuCode || row.id}」？`,
          },
        ),
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
  message.success('状态已更新')
  load()
}

const removeProduct = async (id: EntityId) => {
  await deleteProduct(id)
  message.success('商品已删除')
  load()
}

const uploadImage = async (options: UploadCustomRequestOptions, key: string, onUploaded: (url: string) => void) => {
  const file = options.file.file
  if (!file) {
    message.error('请选择要上传的图片')
    options.onError()
    return
  }
  uploadingKeys.add(key)
  try {
    const result = await uploadFile(file)
    onUploaded(result.url)
    message.success('图片上传成功')
    options.onFinish()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '图片上传失败')
    options.onError()
  } finally {
    uploadingKeys.delete(key)
  }
}

const createUploadRequest = (key: string, onUploaded: (url: string) => void) =>
  (options: UploadCustomRequestOptions) => uploadImage(options, key, onUploaded)

const addProductImageFromUpload = (url: string) => {
  productForm.images.push({
    imageType: productForm.images.some((image) => image.imageType === 1) ? 2 : 1,
    imageUrl: url,
    sortOrder: productForm.images.length,
  })
}

const openSkuDrawer = async (row: AdminProduct) => {
  currentProduct.value = row
  skuDrawer.value = true
  skus.value = await listSkus(row.id)
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
    <div class="page-head">
      <div>
        <h1 class="page-title">商品管理</h1>
        <p class="page-subtitle">管理商品、SKU、分类图片和上下架状态。</p>
      </div>
      <n-space>
        <n-button type="primary" @click="openProductDrawer()">
          <template #icon>
            <n-icon><AddOutline /></n-icon>
          </template>
          新建商品
        </n-button>
      </n-space>
    </div>

    <section class="panel panel-pad">
      <div class="toolbar">
        <n-input v-model:value="query.keyword" clearable placeholder="商品名称 / 编码" style="width: 240px">
          <template #prefix>
            <n-icon><SearchOutline /></n-icon>
          </template>
        </n-input>
        <n-select v-model:value="query.categoryId" clearable :options="categoryOptions" placeholder="分类" style="width: 180px" />
        <n-select v-model:value="query.status" clearable :options="statusOptions" placeholder="状态" style="width: 140px" />
        <n-button type="primary" @click="query.page = 1; load()">查询</n-button>
        <n-button @click="resetFilters">重置</n-button>
        <n-button :loading="loading" circle @click="load">
          <template #icon>
            <n-icon><RefreshOutline /></n-icon>
          </template>
        </n-button>
      </div>
    </section>

    <section class="panel">
      <n-data-table :loading="loading" :columns="columns" :data="rows" :bordered="false" :row-key="productRowKey" />
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

    <n-drawer v-model:show="productDrawer" :width="760">
      <n-drawer-content :title="editingProductId ? '编辑商品' : '新建商品'">
        <div class="drawer-form">
          <n-form label-placement="top">
            <n-grid :cols="2" :x-gap="14">
              <n-form-item-gi label="商品名称">
                <n-input v-model:value="productForm.name" placeholder="输入商品名称" />
              </n-form-item-gi>
              <n-form-item-gi label="分类">
                <n-select v-model:value="productForm.categoryId" :options="categoryOptions" placeholder="选择分类" />
              </n-form-item-gi>
              <n-form-item-gi label="SPU 编码">
                <n-input v-model:value="productForm.spuCode" placeholder="可选" />
              </n-form-item-gi>
              <n-form-item-gi label="品牌 ID">
                <n-input-number v-model:value="productForm.brandId" placeholder="可选" style="width: 100%" />
              </n-form-item-gi>
              <n-form-item-gi label="售价">
                <n-input-number v-model:value="productForm.price" :min="0.01" style="width: 100%" />
              </n-form-item-gi>
              <n-form-item-gi label="原价">
                <n-input-number v-model:value="productForm.oldPrice" :min="0" style="width: 100%" />
              </n-form-item-gi>
              <n-form-item-gi label="库存">
                <n-input-number v-model:value="productForm.inventory" :min="0" style="width: 100%" />
              </n-form-item-gi>
              <n-form-item-gi label="状态">
                <n-select v-model:value="productForm.status" :options="statusOptions" />
              </n-form-item-gi>
            </n-grid>
            <n-form-item label="副标题">
              <n-input v-model:value="productForm.subtitle" />
            </n-form-item>
            <n-form-item label="描述">
              <n-input v-model:value="productForm.description" type="textarea" :autosize="{ minRows: 3 }" />
            </n-form-item>

            <n-divider title-placement="left">图片</n-divider>
            <div class="stack">
              <div v-for="(image, index) in productForm.images" :key="index" class="inline-row">
                <n-select v-model:value="image.imageType" :options="[{ label: '主图', value: 1 }, { label: '详情图', value: 2 }]" style="width: 120px" />
                <n-input v-model:value="image.imageUrl" placeholder="图片 URL" />
                <n-input-number v-model:value="image.sortOrder" placeholder="排序" style="width: 110px" />
                <n-button @click="productForm.images.splice(index, 1)">移除</n-button>
              </div>
              <n-upload
                accept="image/*"
                :show-file-list="false"
                :custom-request="createUploadRequest('product-add', addProductImageFromUpload)"
              >
                <n-button dashed block :loading="uploadingKeys.has('product-add')">添加图片</n-button>
              </n-upload>
            </div>

            <n-divider title-placement="left">属性</n-divider>
            <div class="stack">
              <div v-for="(prop, index) in productForm.properties" :key="index" class="inline-row">
                <n-input v-model:value="prop.name" placeholder="属性名" />
                <n-input v-model:value="prop.value" placeholder="属性值" />
                <n-input-number v-model:value="prop.sortOrder" placeholder="排序" style="width: 110px" />
                <n-button @click="productForm.properties.splice(index, 1)">移除</n-button>
              </div>
              <n-button dashed @click="productForm.properties.push({ name: '', value: '', sortOrder: productForm.properties.length })">添加属性</n-button>
            </div>

            <n-divider v-if="!editingProductId" title-placement="left">初始 SKU</n-divider>
            <div v-if="!editingProductId" class="stack">
              <div v-for="(sku, index) in productForm.skus" :key="index" class="inline-row">
                <n-input v-model:value="sku.skuCode" placeholder="SKU 编码" />
                <n-input-number v-model:value="sku.price" :min="0.01" placeholder="售价" style="width: 140px" />
                <n-input-number v-model:value="sku.inventory" :min="0" placeholder="库存" style="width: 120px" />
                <n-button @click="productForm.skus.splice(index, 1)">移除</n-button>
              </div>
              <n-button dashed @click="productForm.skus.push({ skuCode: '', price: 0, inventory: 0, status: 1 })">添加 SKU</n-button>
            </div>
          </n-form>
        </div>
        <template #footer>
          <n-space justify="end">
            <n-button @click="productDrawer = false">取消</n-button>
            <n-button type="primary" :loading="productSaving" @click="saveProduct">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="skuDrawer" :width="760">
      <n-drawer-content :title="`SKU 管理 - ${currentProduct?.name || ''}`">
        <n-button type="primary" style="margin-bottom: 12px" @click="openSkuForm()">新增 SKU</n-button>
        <n-data-table :columns="skuColumns" :data="skus" :bordered="false" />
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="skuFormDrawer" :width="460">
      <n-drawer-content :title="editingSkuId ? '编辑 SKU' : '新增 SKU'">
        <n-form label-placement="top">
          <n-form-item label="SKU 编码">
            <n-input v-model:value="skuForm.skuCode" />
          </n-form-item>
          <n-form-item label="售价">
            <n-input-number v-model:value="skuForm.price" :min="0.01" style="width: 100%" />
          </n-form-item>
          <n-form-item label="原价">
            <n-input-number v-model:value="skuForm.oldPrice" :min="0" style="width: 100%" />
          </n-form-item>
          <n-form-item label="库存">
            <n-input-number v-model:value="skuForm.inventory" :min="0" style="width: 100%" />
          </n-form-item>
          <n-form-item label="图片 URL">
            <n-input v-model:value="skuForm.picture" />
          </n-form-item>
          <n-form-item label="状态">
            <n-select v-model:value="skuForm.status" :options="statusOptions" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="skuFormDrawer = false">取消</n-button>
            <n-button type="primary" :loading="skuSaving" @click="saveSku">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

  </div>
</template>

<style scoped>
.product-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.product-name-cell strong {
  color: #17212b;
  font-size: 14px;
}

.product-name-cell span {
  color: #758292;
  font-size: 12px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px 16px;
}

.inline-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
}

@media (max-width: 760px) {
  .inline-row {
    grid-template-columns: 1fr;
  }
}
</style>
