<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns, DropdownOption } from 'naive-ui'
import { NAvatar, NButton, NDropdown, NIcon, useMessage } from 'naive-ui'
import {
  ArrowDownOutline,
  EyeOutline,
  PersonOutline,
  ShieldCheckmarkOutline,
} from '@vicons/ionicons5'
import StatusTag from '@/components/StatusTag.vue'
import { getUser, listUsers, updateUserLevel, updateUserStatus } from '@/api/users'
import type { AdminUser, EntityId } from '@/types/admin'

const message = useMessage()
const loading = ref(false)
const rows = ref<AdminUser[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  pageSize: 10,
  keyword: '',
  status: null as number | null,
  memberLevel: null as string | null,
})

const detailDrawer = ref(false)
const currentUser = ref<AdminUser | null>(null)
const levelSaving = ref(false)
const statusOptions = [
  { label: '正常', value: 1 },
  { label: '禁用', value: 0 },
]
const levelOptions = ['NORMAL', 'SILVER', 'GOLD', 'DIAMOND'].map((level) => ({ label: level, value: level }))

const money = (value?: number) =>
  new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(Number(value || 0))

const levelText = (value?: string) => value || 'NORMAL'

const columns: DataTableColumns<AdminUser> = [
  {
    title: '用户',
    key: 'nickname',
    minWidth: 260,
    render(row) {
      const name = row.nickname || row.account || `用户 ${row.id}`
      const sub = row.mobile || row.account || '无账号信息'
      const avatarSlots = row.avatar
        ? undefined
        : { default: () => h(NIcon, { size: 18 }, { default: () => h(PersonOutline) }) }
      return h('div', { class: 'user-cell' }, [
        h(
          NAvatar,
          {
            round: true,
            size: 40,
            src: row.avatar || undefined,
          },
          avatarSlots,
        ),
        h('div', { class: 'user-meta' }, [
          h('div', { class: 'user-name' }, [
            h('strong', { class: 'user-name-text' }, name),
            row.isAdmin === 1
              ? h('span', { class: 'admin-badge' }, '管理员')
              : null,
          ]),
          h('span', { class: 'user-sub' }, sub),
          h('span', { class: 'user-id' }, `ID ${row.id}`),
        ]),
      ])
    },
  },
  {
    title: '会员等级',
    key: 'memberLevel',
    width: 120,
    render: (row) => h('span', { class: 'level-tag' }, levelText(row.memberLevel)),
  },
  {
    title: '余额',
    key: 'balance',
    width: 130,
    render: (row) => {
      const amount = Number(row.balance || 0).toFixed(2)
      return h('span', { class: 'price-cell' }, [
        h('span', { class: 'price-symbol' }, '¥'),
        h('span', { class: 'price-amount' }, amount),
      ])
    },
  },
  {
    title: '成长值',
    key: 'growth',
    width: 110,
    render: (row) => h('span', { class: 'count-cell' }, String(row.growth || 0)),
  },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render: (row) => h(StatusTag, { value: row.status, activeText: '正常', inactiveText: '禁用' }),
  },
  {
    title: '最后登录',
    key: 'lastLoginAt',
    minWidth: 170,
    render: (row) => h('span', { class: 'cell-text' }, row.lastLoginAt || '-'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    fixed: 'right',
    render(row) {
      const dropdownOptions: DropdownOption[] = [
        {
          label: row.status === 1 ? '禁用账号' : '启用账号',
          key: 'toggle-status',
          icon: () => h(NIcon, null, { default: () => h(ShieldCheckmarkOutline) }),
        },
      ]
      return h('div', { class: 'row-actions' }, [
        h(
          NButton,
          { size: 'small', type: 'primary', onClick: () => openDetail(row.id) },
          { default: () => '详情', icon: () => h(NIcon, null, { default: () => h(EyeOutline) }) },
        ),
        h(
          NDropdown,
          {
            trigger: 'click',
            options: dropdownOptions,
            onSelect: (key: string) => {
              if (key === 'toggle-status') toggleStatus(row)
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

const load = async () => {
  loading.value = true
  try {
    const page = await listUsers(query)
    rows.value = page.items
    total.value = page.total
    query.page = page.page
    query.pageSize = page.pageSize
  } catch (error) {
    message.error(error instanceof Error ? error.message : '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  query.keyword = ''
  query.status = null
  query.memberLevel = null
  query.page = 1
  load()
}

const openDetail = async (id: EntityId) => {
  try {
    currentUser.value = await getUser(id)
    detailDrawer.value = true
  } catch (error) {
    message.error(error instanceof Error ? error.message : '用户详情加载失败')
  }
}

const toggleStatus = async (user: AdminUser) => {
  await updateUserStatus(user.id, user.status === 1 ? 0 : 1)
  message.success(user.status === 1 ? '用户已禁用' : '用户已启用')
  load()
}

const saveLevel = async () => {
  if (!currentUser.value?.memberLevel) return
  levelSaving.value = true
  try {
    await updateUserLevel(currentUser.value.id, currentUser.value.memberLevel)
    message.success('会员等级已更新')
    load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '会员等级更新失败')
  } finally {
    levelSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <!-- 页面标题 -->
    <header class="page-head">
      <div class="page-head-text">
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">查询用户资料，调整启停状态和会员等级。</p>
      </div>
    </header>

    <!-- 搜索区 -->
    <section class="panel search-panel">
      <div class="search-row search-row--keyword">
        <n-input
          v-model:value="query.keyword"
          clearable
          placeholder="搜索昵称 / 手机 / 账号"
          class="search-input"
        />
      </div>
      <div class="search-row search-row--filters">
        <n-select
          v-model:value="query.status"
          clearable
          :options="statusOptions"
          placeholder="全部状态"
          class="filter-select"
        />
        <n-select
          v-model:value="query.memberLevel"
          clearable
          :options="levelOptions"
          placeholder="全部会员等级"
          class="filter-select"
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
        :row-key="(row: AdminUser) => row.id"
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

    <!-- 用户详情 Drawer -->
    <n-drawer v-model:show="detailDrawer" :width="520" :auto-focus="false">
      <n-drawer-content title="用户详情" :native-scrollbar="false" closable>
        <div v-if="currentUser" class="drawer-form">
          <!-- 用户概览 -->
          <div class="profile-head">
            <n-avatar
              round
              :size="64"
              :src="currentUser.avatar || undefined"
            >
              <template v-if="!currentUser.avatar">
                <n-icon :size="24"><PersonOutline /></n-icon>
              </template>
            </n-avatar>
            <div class="profile-meta">
              <div class="profile-name">
                <strong>{{ currentUser.nickname || '-' }}</strong>
                <span v-if="currentUser.isAdmin === 1" class="admin-badge">管理员</span>
              </div>
              <span class="profile-sub">{{ currentUser.mobile || currentUser.account || '无账号信息' }}</span>
              <span class="profile-id">ID {{ currentUser.id }}</span>
            </div>
          </div>

          <!-- 资料详情 -->
          <div class="form-section">
            <div class="form-section-title">资料信息</div>
            <n-descriptions bordered :column="1" label-placement="left" size="small">
              <n-descriptions-item label="账号">{{ currentUser.account || '-' }}</n-descriptions-item>
              <n-descriptions-item label="昵称">{{ currentUser.nickname || '-' }}</n-descriptions-item>
              <n-descriptions-item label="手机">{{ currentUser.mobile || '-' }}</n-descriptions-item>
              <n-descriptions-item label="职业">{{ currentUser.profession || '-' }}</n-descriptions-item>
              <n-descriptions-item label="性别">{{ currentUser.gender === 1 ? '男' : currentUser.gender === 2 ? '女' : '未知' }}</n-descriptions-item>
              <n-descriptions-item label="生日">{{ currentUser.birthday || '-' }}</n-descriptions-item>
            </n-descriptions>
          </div>

          <!-- 资产信息 -->
          <div class="form-section">
            <div class="form-section-title">资产与成长</div>
            <div class="stat-grid">
              <div class="stat-card">
                <span class="stat-label">余额</span>
                <span class="stat-value">{{ money(currentUser.balance) }}</span>
              </div>
              <div class="stat-card">
                <span class="stat-label">成长值</span>
                <span class="stat-value">{{ currentUser.growth || 0 }}</span>
              </div>
              <div class="stat-card">
                <span class="stat-label">会员等级</span>
                <span class="stat-value">{{ levelText(currentUser.memberLevel) }}</span>
              </div>
              <div class="stat-card">
                <span class="stat-label">最后登录</span>
                <span class="stat-value stat-value--sm">{{ currentUser.lastLoginAt || '-' }}</span>
              </div>
            </div>
          </div>

          <!-- 运营调整 -->
          <div class="form-section">
            <div class="form-section-title">运营调整</div>
            <n-form label-placement="top">
              <n-form-item label="会员等级">
                <n-select v-model:value="currentUser.memberLevel" :options="levelOptions" />
              </n-form-item>
            </n-form>
          </div>
        </div>

        <template #footer>
          <div class="drawer-footer">
            <n-button @click="detailDrawer = false">关闭</n-button>
            <n-button type="primary" :loading="levelSaving" @click="saveLevel">保存等级</n-button>
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

.search-row--keyword {
  width: 100%;
}

.search-input {
  width: 100%;
  max-width: 420px;
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

/* —— 用户列 —— */
.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.user-name {
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-name-text {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: var(--font-weight-medium);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-badge {
  padding: 1px 6px;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
  border-radius: var(--radius-tag);
  font-size: 11px;
  font-weight: var(--font-weight-medium);
  line-height: 1.5;
}

.user-sub {
  color: var(--color-text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-id {
  font-family: var(--font-family-mono);
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.cell-text {
  color: var(--color-text-secondary);
  font-size: 13px;
}

/* —— 等级 / 价格 / 数字 —— */
.level-tag {
  display: inline-block;
  padding: 2px 8px;
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-tag);
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: var(--font-weight-medium);
}

.price-cell {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  color: var(--color-text-primary);
}

.price-symbol {
  font-size: 12px;
}

.price-amount {
  font-size: 15px;
  font-weight: var(--font-weight-semibold);
}

.count-cell {
  font-size: 14px;
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

/* —— 操作区 —— */
.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* —— Drawer —— */
.drawer-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 4px 20px;
}

.profile-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.profile-name {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: var(--font-weight-semibold);
}

.profile-sub {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.profile-id {
  font-family: var(--font-family-mono);
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.form-section {
  padding: 16px 0;
  border-top: 1px solid var(--color-border);
}

.form-section-title {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: var(--font-weight-semibold);
  margin-bottom: 12px;
  letter-spacing: 0.2px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
}

.stat-label {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.stat-value {
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: var(--font-weight-semibold);
}

.stat-value--sm {
  font-size: 13px;
  font-weight: var(--font-weight-medium);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
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

  .filter-select,
  .search-input {
    width: 100%;
    max-width: none;
  }

  .search-row-actions {
    margin-left: 0;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
