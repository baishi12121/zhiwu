<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import type { DataTableColumns } from 'naive-ui'
import { NButton, NSpace, useMessage } from 'naive-ui'
import { RefreshOutline, SearchOutline } from '@vicons/ionicons5'
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

const columns: DataTableColumns<AdminUser> = [
  {
    title: 'ID',
    key: 'id',
    width: 190,
    render(row) {
      return h('span', { class: 'id-cell' }, String(row.id))
    },
  },
  {
    title: '用户',
    key: 'nickname',
    minWidth: 220,
    render(row) {
      return h('div', { class: 'user-cell' }, [
        h('strong', row.nickname || row.account || `用户 ${row.id}`),
        h('span', row.mobile || row.account || '无账号信息'),
      ])
    },
  },
  { title: '会员等级', key: 'memberLevel' },
  { title: '余额', key: 'balance', render: (row) => money(row.balance) },
  { title: '成长值', key: 'growth' },
  { title: '管理员', key: 'isAdmin', render: (row) => (row.isAdmin === 1 ? '是' : '否') },
  { title: '状态', key: 'status', render: (row) => h(StatusTag, { value: row.status, activeText: '正常', inactiveText: '禁用' }) },
  {
    title: '操作',
    key: 'actions',
    width: 230,
    render(row) {
      return h(NSpace, { size: 8 }, () => [
        h(NButton, { size: 'small', onClick: () => openDetail(row.id) }, { default: () => '详情' }),
        h(
          NButton,
          { size: 'small', type: row.status === 1 ? 'warning' : 'success', onClick: () => toggleStatus(row) },
          { default: () => (row.status === 1 ? '禁用' : '启用') },
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

const reset = () => {
  query.keyword = ''
  query.status = null
  query.memberLevel = null
  query.page = 1
  load()
}

const openDetail = async (id: EntityId) => {
  currentUser.value = await getUser(id)
  detailDrawer.value = true
}

const toggleStatus = async (user: AdminUser) => {
  await updateUserStatus(user.id, user.status === 1 ? 0 : 1)
  message.success('用户状态已更新')
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
    <div class="page-head">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">查询用户资料，调整启停状态和会员等级。</p>
      </div>
    </div>

    <section class="panel panel-pad">
      <div class="toolbar">
        <n-input v-model:value="query.keyword" clearable placeholder="昵称 / 手机 / 账号" style="width: 240px">
          <template #prefix>
            <n-icon><SearchOutline /></n-icon>
          </template>
        </n-input>
        <n-select v-model:value="query.status" clearable :options="statusOptions" placeholder="状态" style="width: 140px" />
        <n-select v-model:value="query.memberLevel" clearable :options="levelOptions" placeholder="会员等级" style="width: 160px" />
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

    <n-drawer v-model:show="detailDrawer" :width="520">
      <n-drawer-content title="用户详情">
        <n-descriptions v-if="currentUser" bordered :column="1" label-placement="left">
          <n-descriptions-item label="用户 ID">{{ currentUser.id }}</n-descriptions-item>
          <n-descriptions-item label="账号">{{ currentUser.account || '-' }}</n-descriptions-item>
          <n-descriptions-item label="昵称">{{ currentUser.nickname || '-' }}</n-descriptions-item>
          <n-descriptions-item label="手机">{{ currentUser.mobile || '-' }}</n-descriptions-item>
          <n-descriptions-item label="职业">{{ currentUser.profession || '-' }}</n-descriptions-item>
          <n-descriptions-item label="余额">{{ money(currentUser.balance) }}</n-descriptions-item>
          <n-descriptions-item label="成长值">{{ currentUser.growth || 0 }}</n-descriptions-item>
          <n-descriptions-item label="最后登录">{{ currentUser.lastLoginAt || '-' }}</n-descriptions-item>
        </n-descriptions>
        <n-divider title-placement="left">运营调整</n-divider>
        <n-form v-if="currentUser" label-placement="top">
          <n-form-item label="会员等级">
            <n-select v-model:value="currentUser.memberLevel" :options="levelOptions" />
          </n-form-item>
          <n-button type="primary" :loading="levelSaving" @click="saveLevel">保存等级</n-button>
        </n-form>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<style scoped>
.user-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.id-cell {
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  white-space: nowrap;
}

.user-cell strong {
  color: #17212b;
  font-size: 14px;
}

.user-cell span {
  color: #758292;
  font-size: 12px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px 16px;
}
</style>
