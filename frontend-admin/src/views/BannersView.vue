<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns, DropdownOption, UploadCustomRequestOptions } from 'naive-ui'
import { NButton, NDropdown, NIcon, NImage, useDialog, useMessage } from 'naive-ui'
import {
  AddOutline,
  ArrowDownOutline,
  ImagesOutline,
  PricetagsOutline,
  TrashOutline,
} from '@vicons/ionicons5'
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
const dialog = useDialog()
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

const columns: DataTableColumns<AdminBanner> = [
  {
    title: '图片',
    key: 'imgUrl',
    width: 120,
    render(row) {
      return row.imgUrl
        ? h(NImage, {
            src: row.imgUrl,
            width: 96,
            height: 54,
            objectFit: 'cover',
            class: 'banner-thumb',
            previewDisabled: true,
          })
        : h('div', { class: 'banner-thumb banner-thumb--empty' }, [
            h(NIcon, { size: 20, color: '#98A2B3' }, { default: () => h(ImagesOutline) }),
          ])
    },
  },
  {
    title: 'Banner',
    key: 'title',
    minWidth: 200,
    render(row) {
      return h('div', { class: 'banner-meta' }, [
        h('strong', { class: 'banner-title' }, row.title),
        h('span', { class: 'banner-href' }, row.hrefUrl ? '已设置跳转' : '未设置跳转'),
      ])
    },
  },
  {
    title: '位置',
    key: 'distributionSite',
    width: 110,
    render: (row) => h('span', { class: 'cell-text' }, siteText(row.distributionSite)),
  },
  { title: '排序', key: 'sortOrder', width: 90, render: (row) => h('span', { class: 'cell-text' }, String(row.sortOrder ?? 0)) },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render: (row) => h(StatusTag, { value: row.status, activeText: '启用', inactiveText: '禁用' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    fixed: 'right',
    render(row) {
      const dropdownOptions: DropdownOption[] = [
        {
          label: row.status === 1 ? '禁用 Banner' : '启用 Banner',
          key: 'toggle-status',
          icon: () => h(NIcon, null, { default: () => h(PricetagsOutline) }),
        },
        { type: 'divider', key: 'd1' },
        {
          label: '删除 Banner',
          key: 'delete',
          icon: () => h(NIcon, { color: '#D03050' }, { default: () => h(TrashOutline) }),
        },
      ]
      return h('div', { class: 'row-actions' }, [
        h(NButton, { size: 'small', type: 'primary', onClick: () => openDrawer(row.id) }, { default: () => '编辑' }),
        h(
          NDropdown,
          {
            trigger: 'click',
            options: dropdownOptions,
            onSelect: (key: string) => {
              if (key === 'toggle-status') toggleStatus(row)
              if (key === 'delete') confirmDeleteBanner(row)
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

const resetFilters = () => {
  query.distributionSite = null
  query.status = null
  query.page = 1
  load()
}

const openDrawer = async (id?: EntityId) => {
  editingId.value = id || null
  Object.assign(form, blankForm())
  if (id) {
    try {
      Object.assign(form, await getBanner(id))
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Banner 详情加载失败')
      return
    }
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
  message.success(row.status === 1 ? 'Banner 已禁用' : 'Banner 已启用')
  load()
}

const confirmDeleteBanner = (row: AdminBanner) => {
  dialog.warning({
    title: '确认删除',
    content: `确认删除 Banner「${row.title}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => remove(row.id),
  })
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
    <!-- 页面标题 -->
    <header class="page-head">
      <div class="page-head-text">
        <h1 class="page-title">Banner 管理</h1>
        <p class="page-subtitle">配置小程序首页和分类页横幅。</p>
      </div>
      <div class="page-head-actions">
        <n-button type="primary" @click="openDrawer()">
          <template #icon>
            <n-icon><AddOutline /></n-icon>
          </template>
          新建 Banner
        </n-button>
      </div>
    </header>

    <!-- 搜索区 -->
    <section class="panel search-panel">
      <div class="search-row search-row--filters">
        <n-select
          v-model:value="query.distributionSite"
          clearable
          :options="siteOptions"
          placeholder="全部位置"
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
        :row-key="(row: AdminBanner) => row.id"
        :scroll-x="780"
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

    <!-- 新建/编辑 Banner Drawer -->
    <n-drawer v-model:show="drawer" :width="620" :auto-focus="false">
      <n-drawer-content
        :title="editingId ? '编辑 Banner' : '新建 Banner'"
        :native-scrollbar="false"
        closable
      >
        <div class="drawer-form">
          <n-form label-placement="top">
            <n-grid :cols="2" :x-gap="16">
              <n-form-item-gi label="标题" :required="true">
                <n-input v-model:value="form.title" placeholder="Banner 标题" />
              </n-form-item-gi>
              <n-form-item-gi label="投放位置" :required="true">
                <n-select v-model:value="form.distributionSite" :options="siteOptions" />
              </n-form-item-gi>
              <n-form-item-gi label="跳转类型" :required="true">
                <n-select v-model:value="form.type" :options="typeOptions" />
              </n-form-item-gi>
              <n-form-item-gi label="排序">
                <n-input-number v-model:value="form.sortOrder" :min="0" style="width: 100%" />
              </n-form-item-gi>
              <n-form-item-gi label="状态">
                <n-select v-model:value="form.status" :options="statusOptions" />
              </n-form-item-gi>
            </n-grid>

            <n-form-item label="图片" :required="true">
              <div class="banner-image-field">
                <n-upload accept="image/*" :show-file-list="false" :custom-request="uploadBannerImage">
                  <n-button :loading="bannerUploading">
                    <template #icon><n-icon><ImagesOutline /></n-icon></template>
                    {{ form.imgUrl ? '更换图片' : '上传图片' }}
                  </n-button>
                </n-upload>
                <span v-if="form.imgUrl" class="banner-image-hint">建议尺寸 750×320</span>
              </div>
              <div v-if="form.imgUrl" class="banner-preview">
                <img :src="form.imgUrl" alt="Banner preview" />
              </div>
            </n-form-item>

            <n-form-item label="跳转 URL">
              <n-input v-model:value="form.hrefUrl" placeholder="/pages/goods/goods?id=1" />
            </n-form-item>

            <n-grid :cols="2" :x-gap="16">
              <n-form-item-gi label="开始时间">
                <n-date-picker v-model:formatted-value="form.startTime" value-format="yyyy-MM-dd'T'HH:mm:ss" type="datetime" clearable style="width: 100%" />
              </n-form-item-gi>
              <n-form-item-gi label="结束时间">
                <n-date-picker v-model:formatted-value="form.endTime" value-format="yyyy-MM-dd'T'HH:mm:ss" type="datetime" clearable style="width: 100%" />
              </n-form-item-gi>
            </n-grid>
          </n-form>
        </div>

        <template #footer>
          <div class="drawer-footer">
            <n-button @click="drawer = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="save">
              {{ editingId ? '保存修改' : '保存 Banner' }}
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

.filter-select--sm {
  width: 150px;
}

.search-row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

/* —— Banner 缩略图列 —— */
.banner-thumb {
  width: 96px !important;
  min-width: 96px;
  max-width: 96px;
  height: 54px !important;
  max-height: 54px;
  border-radius: var(--radius-image);
  overflow: hidden;
  flex-shrink: 0;
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  display: grid;
  place-items: center;
}

.banner-thumb--empty {
  display: grid;
  place-items: center;
}

/* —— Banner 信息列 —— */
.banner-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.banner-title {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: var(--font-weight-medium);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-href {
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
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.banner-image-field {
  display: flex;
  align-items: center;
  gap: 12px;
}

.banner-image-hint {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.banner-preview {
  width: 100%;
  max-width: 480px;
  margin-top: 12px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-surface-subtle);
  aspect-ratio: 750 / 320;
}

.banner-preview img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

/* —— Responsive —— */
@media (max-width: 640px) {
  .banner-image-field {
    flex-direction: column;
    align-items: flex-start;
  }

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
}
</style>
