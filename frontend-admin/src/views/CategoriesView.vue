<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns } from 'naive-ui'
import {
  NButton,
  NIcon,
  NImage,
  NInput,
  NInputNumber,
  NModal,
  NPopconfirm,
  NSelect,
  NSpace,
  useMessage,
} from 'naive-ui'
import {
  AddOutline,
  CreateOutline,
  ImagesOutline,
  TrashOutline,
} from '@vicons/ionicons5'
import StatusTag from '@/components/StatusTag.vue'
import {
  buildCategoryTree,
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory,
  type CategoryTreeNode,
} from '@/api/categories'
import { uploadFile } from '@/api/products'
import type { AdminCategory, CategorySaveRequest, EntityId } from '@/types/admin'

const message = useMessage()
const loading = ref(false)
const categories = ref<AdminCategory[]>([])
const treeData = ref<CategoryTreeNode[]>([])

const modalOpen = ref(false)
const saving = ref(false)
const uploadingIcon = ref(false)
const uploadingPicture = ref(false)
const editingId = ref<EntityId | null>(null)
const presetParentId = ref<EntityId | null>(null)
const form = reactive<CategorySaveRequest>({
  parentId: 0,
  name: '',
  icon: '',
  picture: '',
  sortOrder: 0,
  status: 1,
})

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

const parentOptions = computed(() => {
  const opts: { label: string; value: EntityId }[] = [{ label: '（顶级分类）', value: 0 }]
  for (const c of categories.value) {
    const pid = c.parentId ?? 0
    if (pid === 0 || pid === '0') {
      opts.push({ label: c.name, value: c.id })
    }
  }
  return opts
})

const isImageUrl = (v?: string | null) => !!v && /^https?:\/\//i.test(v.trim())

const renderCategoryThumb = (row: CategoryTreeNode) => {
  if (isImageUrl(row.picture)) {
    return h(NImage, {
      src: row.picture!,
      width: 64,
      height: 64,
      objectFit: 'cover',
      class: 'cat-thumb',
      previewDisabled: true,
    })
  }
  if (isImageUrl(row.icon)) {
    return h(NImage, {
      src: row.icon!,
      width: 64,
      height: 64,
      objectFit: 'cover',
      class: 'cat-thumb',
      previewDisabled: true,
    })
  }
  if (row.icon && row.icon.trim()) {
    return h('div', { class: 'cat-thumb cat-thumb--emoji' }, row.icon.trim())
  }
  return h('div', { class: 'cat-thumb cat-thumb--empty' }, [
    h(NIcon, { size: 20, color: '#98A2B3' }, { default: () => h(ImagesOutline) }),
  ])
}

const columns: DataTableColumns<CategoryTreeNode> = [
  {
    title: '分类图片',
    key: 'picture',
    width: 100,
    render: renderCategoryThumb,
  },
  {
    title: '分类名称',
    key: 'name',
    minWidth: 220,
    render(row) {
      return h('div', { class: 'cat-name-cell' }, [
        h('strong', { class: 'cat-name' }, row.name),
        !row.parentId || row.parentId === 0
          ? h('span', { class: 'cat-level-tag cat-level-tag--root' }, '顶级')
          : null,
      ])
    },
  },
  {
    title: 'ID',
    key: 'id',
    width: 90,
    render: (row) => h('span', { class: 'cell-text cell-mono' }, String(row.id)),
  },
  {
    title: '排序',
    key: 'sortOrder',
    width: 90,
    render: (row) => h('span', { class: 'cell-text' }, String(row.sortOrder ?? 0)),
  },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render: (row) => h(StatusTag, { value: row.status, activeText: '启用', inactiveText: '禁用' }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 260,
    fixed: 'right',
    render(row) {
      const isRoot = !row.parentId || row.parentId === 0
      return h(NSpace, { size: 6 }, {
        default: () => [
          isRoot
            ? h(
                NButton,
                {
                  size: 'small',
                  type: 'primary',
                  ghost: true,
                  onClick: () => openModal(null, row.id),
                },
                {
                  default: () => '添加子分类',
                  icon: () => h(NIcon, null, { default: () => h(AddOutline) }),
                },
              )
            : null,
          h(
            NButton,
            { size: 'small', type: 'primary', onClick: () => openModal(row.id) },
            {
              default: () => '编辑',
              icon: () => h(NIcon, null, { default: () => h(CreateOutline) }),
            },
          ),
          h(
            NButton,
            {
              size: 'small',
              type: row.status === 1 ? 'warning' : 'success',
              ghost: true,
              onClick: () => toggleStatus(row),
            },
            { default: () => (row.status === 1 ? '禁用' : '启用') },
          ),
          h(
            NPopconfirm,
            { onPositiveClick: () => remove(row) },
            {
              trigger: () =>
                h(
                  NButton,
                  { size: 'small', type: 'error', ghost: true },
                  {
                    default: () => '删除',
                    icon: () => h(NIcon, null, { default: () => h(TrashOutline) }),
                  },
                ),
              default: () => `确认删除分类「${row.name}」？${row.children?.length ? '该分类下有子分类，需先删除子分类。' : ''}`,
            },
          ),
        ].filter(Boolean),
      })
    },
  },
]

const load = async () => {
  loading.value = true
  try {
    categories.value = await listCategories()
    treeData.value = buildCategoryTree(categories.value)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '分类加载失败')
  } finally {
    loading.value = false
  }
}

const openModal = (id?: EntityId | null, parentId?: EntityId | null) => {
  editingId.value = id ?? null
  presetParentId.value = parentId ?? null
  if (id) {
    const cat = categories.value.find((c) => c.id === id)
    if (cat) {
      form.parentId = cat.parentId ?? 0
      form.name = cat.name
      form.icon = cat.icon ?? ''
      form.picture = cat.picture ?? ''
      form.sortOrder = cat.sortOrder ?? 0
      form.status = cat.status ?? 1
    }
  } else {
    form.parentId = parentId ?? 0
    form.name = ''
    form.icon = ''
    form.picture = ''
    form.sortOrder = 0
    form.status = 1
  }
  modalOpen.value = true
}

const closeModal = () => {
  modalOpen.value = false
}

const handleSubmit = async () => {
  if (!form.name || !form.name.trim()) {
    message.warning('请填写分类名称')
    return
  }
  saving.value = true
  try {
    const payload: CategorySaveRequest = {
      parentId: form.parentId ?? 0,
      name: form.name.trim(),
      icon: form.icon || null,
      picture: form.picture || null,
      sortOrder: form.sortOrder ?? 0,
      status: form.status ?? 1,
    }
    if (editingId.value) {
      await updateCategory(editingId.value, payload)
      message.success('分类已更新')
    } else {
      await createCategory(payload)
      message.success('分类已添加')
    }
    modalOpen.value = false
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

const remove = async (row: CategoryTreeNode) => {
  try {
    await deleteCategory(row.id)
    message.success('分类已删除')
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败')
  }
}

const toggleStatus = async (row: CategoryTreeNode) => {
  try {
    await updateCategory(row.id, {
      parentId: row.parentId,
      name: row.name,
      icon: row.icon ?? null,
      picture: row.picture ?? null,
      sortOrder: row.sortOrder,
      status: row.status === 1 ? 0 : 1,
    })
    message.success(row.status === 1 ? '已禁用' : '已启用')
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败')
  }
}

const beforeUpload = ({ file }: { file: File }) => {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    message.error('只能上传图片')
    return false
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

const handleIconUpload = async ({ file, onFinish, onError }: any) => {
  if (!beforeUpload({ file: file.file })) {
    onError()
    return
  }
  uploadingIcon.value = true
  try {
    const res = await uploadFile(file.file)
    form.icon = res.url
    message.success('图标上传成功')
    onFinish()
  } catch (e) {
    message.error('图标上传失败')
    onError()
  } finally {
    uploadingIcon.value = false
  }
}

const handlePictureUpload = async ({ file, onFinish, onError }: any) => {
  if (!beforeUpload({ file: file.file })) {
    onError()
    return
  }
  uploadingPicture.value = true
  try {
    const res = await uploadFile(file.file)
    form.picture = res.url
    message.success('图片上传成功')
    onFinish()
  } catch (e) {
    message.error('图片上传失败')
    onError()
  } finally {
    uploadingPicture.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>分类管理</h2>
        <p class="page-sub">维护商品分类结构，支持两级分类（顶级 / 子分类）。</p>
      </div>
      <n-button type="primary" @click="openModal()">
        <template #icon>
          <n-icon><AddOutline /></n-icon>
        </template>
        新增顶级分类
      </n-button>
    </div>

    <div class="card">
      <n-data-table
        :columns="columns"
        :data="treeData"
        :loading="loading"
        :row-key="(row: CategoryTreeNode) => String(row.id)"
        default-expand-all
        :scroll-x="900"
      />
    </div>

    <n-modal
      v-model:show="modalOpen"
      preset="card"
      :title="editingId ? '编辑分类' : '新增分类'"
      style="width: 520px"
      :mask-closable="false"
    >
      <n-form
        label-placement="left"
        label-width="90px"
        :model="form"
        class="cat-form"
      >
        <n-form-item label="上级分类">
          <n-select
            v-model:value="form.parentId"
            :options="parentOptions"
            placeholder="选择上级分类"
          />
        </n-form-item>
        <n-form-item label="分类名称" required>
          <n-input v-model:value="form.name" placeholder="例如：水果" maxlength="20" />
        </n-form-item>
        <n-form-item label="分类图标">
          <div class="upload-field upload-field--icon">
            <n-input
              v-model:value="form.icon"
              placeholder="输入 emoji（如 👗）或图片 URL"
              clearable
              class="icon-input"
            />
            <n-upload
              accept="image/*"
              :show-file-list="false"
              :custom-request="handleIconUpload"
            >
              <n-button :loading="uploadingIcon">
                <template #icon>
                  <n-icon><ImagesOutline /></n-icon>
                </template>
                上传
              </n-button>
            </n-upload>
            <div v-if="form.icon" class="icon-preview-wrap">
              <n-image
                v-if="isImageUrl(form.icon)"
                :src="form.icon"
                width="40"
                height="40"
                object-fit="contain"
                preview-disabled
                class="upload-preview"
              />
              <div v-else class="icon-preview-emoji">{{ form.icon }}</div>
            </div>
          </div>
          <span class="field-hint">可直接粘贴 emoji 字符，或上传图片</span>
        </n-form-item>
        <n-form-item label="分类图片">
          <div class="upload-field upload-field--picture">
            <n-upload
              accept="image/*"
              :show-file-list="false"
              :custom-request="handlePictureUpload"
            >
              <n-button :loading="uploadingPicture">
                <template #icon>
                  <n-icon><ImagesOutline /></n-icon>
                </template>
                {{ form.picture ? '更换图片' : '上传图片' }}
              </n-button>
            </n-upload>
            <n-image
              v-if="isImageUrl(form.picture)"
              :src="form.picture!"
              width="120"
              height="60"
              object-fit="cover"
              preview-disabled
              class="upload-preview upload-preview--banner"
            />
            <span class="field-hint">建议 750×320，小程序分类页 Banner 展示用（可选）</span>
          </div>
        </n-form-item>
        <n-form-item label="排序">
          <n-input-number v-model:value="form.sortOrder" :min="0" style="width: 160px" />
        </n-form-item>
        <n-form-item label="状态">
          <n-select v-model:value="form.status" :options="statusOptions" style="width: 160px" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px">
          <n-button @click="closeModal">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSubmit">
            {{ editingId ? '保存' : '新增' }}
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.page-sub {
  margin: 4px 0 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-aux);
}

.card {
  background: #ffffff;
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border-light);
  overflow: hidden;
}

.cell-text {
  color: var(--color-text-secondary);
  font-size: var(--font-size-body);
}

.cell-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12.5px;
  color: var(--color-text-tertiary);
}

.cat-thumb {
  width: 64px !important;
  height: 64px !important;
  border-radius: var(--radius-image);
  overflow: hidden;
  flex-shrink: 0;
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  display: grid;
  place-items: center;
}

.cat-thumb--emoji {
  font-size: 30px;
  line-height: 1;
  background: linear-gradient(135deg, rgba(24, 160, 88, 0.08), rgba(24, 160, 88, 0.02));
}

.cat-thumb--empty {
  display: grid;
  place-items: center;
}

.cat-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cat-name {
  font-size: var(--font-size-body);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.cat-level-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  line-height: 1.6;
  font-weight: var(--font-weight-medium);
}

.cat-level-tag--root {
  background: rgba(24, 160, 88, 0.1);
  color: var(--color-primary-pressed);
}

/* —— 表单 —— */
.cat-form {
  padding: 4px 0;
}

.upload-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.upload-field--icon {
  flex-wrap: wrap;
}

.icon-input {
  flex: 1;
  min-width: 200px;
}

.icon-preview-wrap {
  display: flex;
  align-items: center;
}

.icon-preview-emoji {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  font-size: 22px;
  line-height: 1;
  border-radius: var(--radius-image);
  background: linear-gradient(135deg, rgba(24, 160, 88, 0.08), rgba(24, 160, 88, 0.02));
  border: 1px solid var(--color-border);
}

.upload-field--picture {
  flex-wrap: wrap;
  align-items: flex-start;
}

.upload-preview {
  border-radius: var(--radius-image);
  border: 1px solid var(--color-border);
  overflow: hidden;
  background: var(--color-surface-subtle);
}

.upload-preview--banner {
  width: 120px;
  height: 60px;
}

.field-hint {
  width: 100%;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-caption);
}

:deep(.n-data-table .n-data-table-td) {
  vertical-align: middle;
}
</style>
