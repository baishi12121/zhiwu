<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns, UploadCustomRequestOptions } from 'naive-ui'
import { NButton, NPopconfirm, NSpace, useMessage } from 'naive-ui'
import { AddOutline, RefreshOutline } from '@vicons/ionicons5'
import StatusTag from '@/components/StatusTag.vue'
import {
  createBanner,
  deleteBanner,
  getBanner,
  listBanners,
  updateBanner,
  updateBannerStatus,
} from '@/api/banners'
import { uploadFile } from '@/api/products'
import type { AdminBanner, EntityId } from '@/types/admin'

const message = useMessage()
const loading = ref(false)
const rows = ref<AdminBanner[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  pageSize: 10,
  distributionSite: null as number | null,
  status: null as number | null,
})

const drawer = ref(false)
const saving = ref(false)
const bannerUploading = ref(false)
const editingId = ref<EntityId | null>(null)
const form = reactive<Partial<AdminBanner>>({
  title: '',
  imgUrl: '',
  hrefUrl: '',
  type: 1,
  distributionSite: 1,
  sortOrder: 0,
  status: 1,
  startTime: undefined,
  endTime: undefined,
})

const siteOptions = [
  { label: '首页', value: 1 },
  { label: '分类页', value: 2 },
]
const typeOptions = [
  { label: '小程序页面', value: 1 },
  { label: 'H5 链接', value: 2 },
  { label: '小程序', value: 3 },
]
const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]
const siteText = (value?: number) => siteOptions.find((item) => item.value === value)?.label || '-'

const thumbStyle = {
  width: '104px',
  height: '54px',
  maxWidth: '104px',
  maxHeight: '54px',
  display: 'block',
  objectFit: 'cover',
  borderRadius: '6px',
  background: '#eef3f2',
}

const columns: DataTableColumns<AdminBanner> = [
  { title: 'ID', key: 'id', width: 80 },
  {
    title: '图片',
    key: 'imgUrl',
    width: 130,
    render(row) {
      return h('div', { class: 'banner-thumb-wrap' }, [
        row.imgUrl ? h('img', { style: thumbStyle, src: row.imgUrl, alt: row.title }) : h('span', '无图'),
      ])
    },
  },
  {
    title: '标题',
    key: 'title',
    minWidth: 180,
    render(row) {
      return h('div', { class: 'title-cell' }, [
        h('strong', row.title),
        h('span', row.hrefUrl || '未设置跳转'),
      ])
    },
  },
  { title: '位置', key: 'distributionSite', render: (row) => siteText(row.distributionSite) },
  { title: '排序', key: 'sortOrder', width: 90 },
  {
    title: '状态',
    key: 'status',
    render: (row) => h(StatusTag, { value: row.status, activeText: '启用', inactiveText: '禁用' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 250,
    render(row) {
      return h(NSpace, { size: 8 }, () => [
        h(NButton, { size: 'small', onClick: () => openDrawer(row.id) }, { default: () => '编辑' }),
        h(
          NButton,
          { size: 'small', type: row.status === 1 ? 'warning' : 'success', onClick: () => toggleStatus(row) },
          { default: () => (row.status === 1 ? '禁用' : '启用') },
        ),
        h(
          NPopconfirm,
          { onPositiveClick: () => remove(row.id) },
          {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
            default: () => `确认删除 Banner「${row.title}」？`,
          },
        ),
      ])
    },
  },
]

const blankForm = () => ({
  title: '',
  imgUrl: '',
  hrefUrl: '',
  type: 1,
  distributionSite: 1,
  sortOrder: 0,
  status: 1,
  startTime: undefined,
  endTime: undefined,
})

const load = async () => {
  loading.value = true
  try {
    const page = await listBanners(query)
    rows.value = page.items
    total.value = page.total
    query.page = page.page
    query.pageSize = page.pageSize
  } catch (error) {
    message.error(error instanceof Error ? error.message : 'Banner 加载失败')
  } finally {
    loading.value = false
  }
}

const openDrawer = async (id?: EntityId) => {
  editingId.value = id || null
  Object.assign(form, blankForm())
  if (id) {
    Object.assign(form, await getBanner(id))
  }
  drawer.value = true
}

const uploadBannerImage = async (options: UploadCustomRequestOptions) => {
  const file = options.file.file
  if (!file) {
    message.error('请选择要上传的图片')
    options.onError()
    return
  }
  bannerUploading.value = true
  try {
    const result = await uploadFile(file)
    form.imgUrl = result.url
    message.success('图片上传成功')
    options.onFinish()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '图片上传失败')
    options.onError()
  } finally {
    bannerUploading.value = false
  }
}

const save = async () => {
  if (!form.title || !form.imgUrl || !form.distributionSite || !form.type) {
    message.warning('请填写标题、图片、位置和跳转类型')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateBanner(editingId.value, form)
      message.success('Banner 已更新')
    } else {
      await createBanner(form)
      message.success('Banner 已创建')
    }
    drawer.value = false
    load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (row: AdminBanner) => {
  await updateBannerStatus(row.id, row.status === 1 ? 0 : 1)
  message.success('状态已更新')
  load()
}

const remove = async (id: EntityId) => {
  await deleteBanner(id)
  message.success('Banner 已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">Banner 管理</h1>
        <p class="page-subtitle">配置小程序首页和分类页横幅。</p>
      </div>
      <n-button type="primary" @click="openDrawer()">
        <template #icon>
          <n-icon><AddOutline /></n-icon>
        </template>
        新建 Banner
      </n-button>
    </div>

    <section class="panel panel-pad">
      <div class="toolbar">
        <n-select v-model:value="query.distributionSite" clearable :options="siteOptions" placeholder="投放位置" style="width: 160px" />
        <n-select v-model:value="query.status" clearable :options="statusOptions" placeholder="状态" style="width: 140px" />
        <n-button type="primary" @click="query.page = 1; load()">查询</n-button>
        <n-button @click="query.distributionSite = null; query.status = null; query.page = 1; load()">重置</n-button>
        <n-button :loading="loading" circle @click="load">
          <template #icon>
            <n-icon><RefreshOutline /></n-icon>
          </template>
        </n-button>
      </div>
    </section>

    <section class="panel">
      <n-data-table :loading="loading" :columns="columns" :data="rows" :bordered="false" :row-key="(row: AdminBanner) => row.id" />
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

    <n-drawer v-model:show="drawer" :width="620">
      <n-drawer-content :title="editingId ? '编辑 Banner' : '新建 Banner'">
        <n-form label-placement="top">
          <n-grid :cols="2" :x-gap="14">
            <n-form-item-gi label="标题">
              <n-input v-model:value="form.title" />
            </n-form-item-gi>
            <n-form-item-gi label="投放位置">
              <n-select v-model:value="form.distributionSite" :options="siteOptions" />
            </n-form-item-gi>
            <n-form-item-gi label="跳转类型">
              <n-select v-model:value="form.type" :options="typeOptions" />
            </n-form-item-gi>
            <n-form-item-gi label="排序">
              <n-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi label="状态">
              <n-select v-model:value="form.status" :options="statusOptions" />
            </n-form-item-gi>
          </n-grid>
          <n-form-item label="图片 URL">
            <div class="banner-image-field">
              <n-input v-model:value="form.imgUrl" placeholder="上传图片后自动填入，也可手动粘贴 URL" />
              <n-upload accept="image/*" :show-file-list="false" :custom-request="uploadBannerImage">
                <n-button :loading="bannerUploading">上传图片</n-button>
              </n-upload>
            </div>
            <div v-if="form.imgUrl" class="banner-preview">
              <img :src="form.imgUrl" alt="Banner preview" />
            </div>
          </n-form-item>
          <n-form-item label="跳转 URL">
            <n-input v-model:value="form.hrefUrl" placeholder="/pages/goods/goods?id=1" />
          </n-form-item>
          <n-grid :cols="2" :x-gap="14">
            <n-form-item-gi label="开始时间">
              <n-date-picker v-model:formatted-value="form.startTime" value-format="yyyy-MM-dd'T'HH:mm:ss" type="datetime" clearable style="width: 100%" />
            </n-form-item-gi>
            <n-form-item-gi label="结束时间">
              <n-date-picker v-model:formatted-value="form.endTime" value-format="yyyy-MM-dd'T'HH:mm:ss" type="datetime" clearable style="width: 100%" />
            </n-form-item-gi>
          </n-grid>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="drawer = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="save">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<style scoped>
.banner-thumb-wrap {
  width: 104px;
  height: 54px;
  overflow: hidden;
  border-radius: 6px;
  background: #eef3f2;
  color: #8a949f;
  font-size: 12px;
  line-height: 54px;
  text-align: center;
}

.title-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title-cell strong {
  color: #17212b;
  font-size: 14px;
}

.title-cell span {
  color: #758292;
  font-size: 12px;
}

.banner-image-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  width: 100%;
}

.banner-preview {
  width: 100%;
  max-width: 360px;
  margin-top: 10px;
  overflow: hidden;
  border: 1px solid #e5e9ef;
  border-radius: 6px;
  background: #eef3f2;
  aspect-ratio: 2 / 1;
}

.banner-preview img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px 16px;
}

@media (max-width: 640px) {
  .banner-image-field {
    grid-template-columns: 1fr;
  }
}
</style>
