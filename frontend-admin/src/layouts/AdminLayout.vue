<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { DropdownOption, MenuOption } from 'naive-ui'
import { NIcon, useMessage } from 'naive-ui'
import {
  BarChartOutline,
  CubeOutline,
  FlashOutline,
  ImagesOutline,
  LogOutOutline,
  PaperPlaneOutline,
  PeopleOutline,
  PricetagsOutline,
} from '@vicons/ionicons5'
import ShopIcon from '@/components/ShopIcon.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const auth = useAuthStore()
const collapsed = ref(false)
const isMobile = ref(false)

const renderIcon = (icon: object) => () => h(NIcon, { size: 18 }, { default: () => h(icon) })

const renderLink = (to: string, label: string) => () =>
  h(RouterLink, { to, class: 'menu-link' }, { default: () => label })

// 分组菜单：总览 / 商品 / 交易 / 用户
const menuOptions: MenuOption[] = [
  {
    label: renderLink('/dashboard', '运营总览'),
    key: '/dashboard',
    icon: renderIcon(BarChartOutline),
  },
  {
    key: 'group-products',
    type: 'group',
    label: '商品',
    children: [
      { label: renderLink('/products', '商品管理'), key: '/products', icon: renderIcon(CubeOutline) },
      { label: renderLink('/categories', '分类管理'), key: '/categories', icon: renderIcon(PricetagsOutline) },
      { label: renderLink('/banners', 'Banner 管理'), key: '/banners', icon: renderIcon(ImagesOutline) },
      { label: renderLink('/seckill', '秒杀管理'), key: '/seckill', icon: renderIcon(FlashOutline) },
    ],
  },
  {
    key: 'group-trade',
    type: 'group',
    label: '交易',
    children: [
      { label: renderLink('/orders', '订单管理'), key: '/orders', icon: renderIcon(PaperPlaneOutline) },
    ],
  },
  {
    key: 'group-users',
    type: 'group',
    label: '用户',
    children: [
      { label: renderLink('/users', '用户管理'), key: '/users', icon: renderIcon(PeopleOutline) },
    ],
  },
]

const activeKey = computed(() => `/${route.path.split('/')[1] || 'dashboard'}`)
const displayName = computed(() => auth.profile?.nickname || auth.auth?.nickname || '管理员')
const displayLevel = computed(() => auth.profile?.memberLevel || auth.auth?.memberLevel || 'ADMIN')
const siderCollapsed = computed(() => isMobile.value || collapsed.value)

// 面包屑
const routeMeta: Record<string, { title: string; ancestors?: string[] }> = {
  '/dashboard': { title: '运营总览', ancestors: ['首页'] },
  '/products': { title: '商品管理', ancestors: ['首页', '商品'] },
  '/categories': { title: '分类管理', ancestors: ['首页', '商品'] },
  '/banners': { title: 'Banner 管理', ancestors: ['首页', '商品'] },
  '/seckill': { title: '秒杀管理', ancestors: ['首页', '商品'] },
  '/orders': { title: '订单管理', ancestors: ['首页', '交易'] },
  '/users': { title: '用户管理', ancestors: ['首页', '用户'] },
}
const breadcrumb = computed(() => routeMeta[activeKey.value] || { title: '知物商城' })

const updateViewport = () => {
  isMobile.value = window.innerWidth <= 760
}

onMounted(() => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
  auth.loadProfile().catch(() => undefined)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateViewport)
})

const userDropdownOptions: DropdownOption[] = [
  { type: 'group', label: () => h('div', { class: 'user-dropdown-meta' }, [
      h('strong', displayName.value),
      h('span', displayLevel.value),
    ]) },
  { type: 'divider', key: 'd1' },
  {
    label: '退出登录',
    key: 'logout',
    icon: renderIcon(LogOutOutline),
  },
]

const handleUserDropdown = (key: string) => {
  if (key === 'logout') handleLogout()
}

const handleLogout = async () => {
  await auth.logout()
  message.success('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <n-layout has-sider class="admin-shell">
    <!-- Sidebar -->
    <n-layout-sider
      bordered
      collapse-mode="width"
      :collapsed="siderCollapsed"
      :collapsed-width="72"
      :width="232"
      show-trigger
      class="admin-sider"
      :class="{ 'admin-sider--collapsed': siderCollapsed }"
      @update:collapsed="collapsed = $event"
    >
      <!-- Brand area 72px -->
      <div class="brand">
        <div class="brand-mark">
          <ShopIcon :size="22" color="#18A058" />
        </div>
        <div class="brand-text">
          <strong>知物商城</strong>
          <span>Admin Console</span>
        </div>
      </div>

      <nav class="menu-wrap">
        <n-menu
          :value="activeKey"
          :options="menuOptions"
          :collapsed="siderCollapsed"
          :collapsed-width="72"
          :collapsed-icon-size="20"
          :indent="18"
          :root-indent="14"
        />
      </nav>
    </n-layout-sider>

    <n-layout>
      <!-- Header 64px -->
      <n-layout-header bordered class="admin-header">
        <div class="header-left">
          <div class="breadcrumb">
            <template v-for="(crumb, idx) in breadcrumb.ancestors || []" :key="idx">
              <span class="breadcrumb-ancestor">{{ crumb }}</span>
              <span class="breadcrumb-sep">/</span>
            </template>
            <span class="breadcrumb-current">{{ breadcrumb.title }}</span>
          </div>
        </div>

        <div class="header-right">
          <n-tooltip placement="bottom">
            <template #trigger>
              <n-button quaternary circle class="header-icon-btn">
                <template #icon>
                  <ShopIcon :size="18" />
                </template>
              </n-button>
            </template>
            控制台首页
          </n-tooltip>

          <n-dropdown
            trigger="click"
            :options="userDropdownOptions"
            placement="bottom-end"
            @select="handleUserDropdown"
          >
            <div class="user-trigger">
              <n-avatar
                round
                :size="32"
                :src="auth.profile?.avatar || auth.auth?.avatar"
                class="user-avatar"
              >
                {{ displayName.slice(0, 1) }}
              </n-avatar>
              <div class="user-meta">
                <strong>{{ displayName }}</strong>
                <span>{{ displayLevel }}</span>
              </div>
            </div>
          </n-dropdown>
        </div>
      </n-layout-header>

      <!-- Content -->
      <n-layout-content class="admin-content">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
}

/* —— Sidebar —— */
.admin-sider {
  background: #ffffff;
  border-right: 1px solid var(--color-border) !important;
  display: flex;
  flex-direction: column;
}

:deep(.n-layout-sider-scroll-container) {
  display: flex;
  flex-direction: column;
}

/* Brand area */
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: var(--layout-brand-height);
  padding: 0 18px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--color-border-light);
}

.admin-sider--collapsed .brand {
  justify-content: center;
  padding: 0;
  gap: 0;
}

.admin-sider--collapsed .brand-text {
  display: none;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(24, 160, 88, 0.14), rgba(24, 160, 88, 0.06));
  flex-shrink: 0;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.brand-text strong {
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: var(--font-weight-semibold);
  line-height: 1.1;
  letter-spacing: -0.01em;
}

.brand-text span {
  color: var(--color-text-tertiary);
  font-size: 11px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

/* Menu */
.menu-wrap {
  flex: 1;
  padding: 12px 10px;
  overflow-y: auto;
  overflow-x: hidden;
}

:deep(.menu-link) {
  display: block;
  width: 100%;
  color: inherit;
  text-decoration: none;
}

:deep(.n-menu .n-menu-item) {
  margin: 2px 0;
}

:deep(.n-menu .n-menu-item-content) {
  border-radius: 8px;
  position: relative;
}

/* Active 选中：仅保留文字/图标变色，无背景无指示条 */
:deep(.n-menu .n-menu-item-content--selected) {
  background: transparent !important;
}

/* 一级分组标题 */
:deep(.n-menu .n-menu-item-group) {
  margin-top: 14px;
}

:deep(.n-menu .n-menu-item-group .n-menu-item-group-title) {
  padding: 4px 14px 6px;
  color: var(--color-text-tertiary);
  font-size: 11px;
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.admin-sider--collapsed :deep(.n-menu .n-menu-item-group .n-menu-item-group-title) {
  display: none;
}

/* —— Header —— */
.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--layout-header-height);
  padding: 0 24px;
  background: #ffffff;
  border-bottom: 1px solid var(--color-border) !important;
  position: sticky;
  top: 0;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: var(--font-size-body);
}

.breadcrumb-ancestor {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-aux);
}

.breadcrumb-sep {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-caption);
}

.breadcrumb-current {
  color: var(--color-text-primary);
  font-size: var(--font-size-body);
  font-weight: var(--font-weight-medium);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-icon-btn {
  color: var(--color-text-secondary);
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 6px 4px 4px;
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: background var(--motion-fast);
}

.user-trigger:hover {
  background: rgba(31, 41, 55, 0.04);
}

.user-avatar {
  flex-shrink: 0;
  background: rgba(24, 160, 88, 0.14);
  color: var(--color-primary-pressed);
  font-weight: var(--font-weight-semibold);
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.user-meta strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-aux);
  font-weight: var(--font-weight-medium);
  line-height: 1.2;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta span {
  color: var(--color-text-tertiary);
  font-size: 11px;
  line-height: 1.2;
}

/* —— Content —— */
.admin-content {
  min-height: calc(100vh - var(--layout-header-height));
  padding: var(--layout-content-padding);
  background: var(--color-bg);
}

/* —— User dropdown meta —— */
.user-dropdown-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 0;
}

.user-dropdown-meta strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-body);
  font-weight: var(--font-weight-semibold);
}

.user-dropdown-meta span {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-caption);
}

/* —— Responsive —— */
@media (max-width: 760px) {
  .user-meta {
    display: none;
  }

  .admin-header {
    padding: 0 16px;
  }

  .admin-content {
    padding: 14px;
  }

  .breadcrumb-ancestor,
  .breadcrumb-sep {
    display: none;
  }
}
</style>
