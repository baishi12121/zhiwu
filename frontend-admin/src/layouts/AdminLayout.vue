<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { MenuOption } from 'naive-ui'
import { NIcon, useMessage } from 'naive-ui'
import {
  BarChartOutline,
  CubeOutline,
  FlashOutline,
  ImagesOutline,
  LogOutOutline,
  PaperPlaneOutline,
  PeopleOutline,
  StorefrontOutline,
} from '@vicons/ionicons5'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const auth = useAuthStore()
const collapsed = ref(false)
const isMobile = ref(false)

const renderIcon = (icon: object) => () => h(NIcon, null, { default: () => h(icon) })

const menuOptions: MenuOption[] = [
  {
    label: () => h(RouterLink, { to: '/dashboard' }, { default: () => '运营总览' }),
    key: '/dashboard',
    icon: renderIcon(BarChartOutline),
  },
  {
    label: () => h(RouterLink, { to: '/products' }, { default: () => '商品管理' }),
    key: '/products',
    icon: renderIcon(CubeOutline),
  },
  {
    label: () => h(RouterLink, { to: '/banners' }, { default: () => 'Banner 管理' }),
    key: '/banners',
    icon: renderIcon(ImagesOutline),
  },
  {
    label: () => h(RouterLink, { to: '/seckill' }, { default: () => '秒杀管理' }),
    key: '/seckill',
    icon: renderIcon(FlashOutline),
  },
  {
    label: () => h(RouterLink, { to: '/orders' }, { default: () => '订单管理' }),
    key: '/orders',
    icon: renderIcon(PaperPlaneOutline),
  },
  {
    label: () => h(RouterLink, { to: '/users' }, { default: () => '用户管理' }),
    key: '/users',
    icon: renderIcon(PeopleOutline),
  },
]

const activeKey = computed(() => `/${route.path.split('/')[1] || 'dashboard'}`)
const displayName = computed(() => auth.profile?.nickname || auth.auth?.nickname || '管理员')
const siderCollapsed = computed(() => isMobile.value || collapsed.value)

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

const handleLogout = async () => {
  await auth.logout()
  message.success('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <n-layout has-sider class="admin-shell">
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
      <div class="brand">
        <n-icon size="30" color="#0f766e">
          <StorefrontOutline />
        </n-icon>
        <div>
          <strong>植屋商城</strong>
          <span>Admin Console</span>
        </div>
      </div>
      <n-menu :value="activeKey" :options="menuOptions" :collapsed="siderCollapsed" :collapsed-width="72" />
    </n-layout-sider>
    <n-layout>
      <n-layout-header bordered class="admin-header">
        <div>
          <div class="header-title">后台管理中心</div>
          <div class="header-subtitle">商品、Banner、秒杀、用户与销售数据统一管理</div>
        </div>
        <div class="header-user">
          <n-avatar round :src="auth.profile?.avatar || auth.auth?.avatar">
            {{ displayName.slice(0, 1) }}
          </n-avatar>
          <div class="user-meta">
            <strong>{{ displayName }}</strong>
            <span>{{ auth.profile?.memberLevel || auth.auth?.memberLevel || 'ADMIN' }}</span>
          </div>
          <n-button quaternary circle @click="handleLogout">
            <template #icon>
              <n-icon><LogOutOutline /></n-icon>
            </template>
          </n-button>
        </div>
      </n-layout-header>
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

.admin-sider {
  background: #fbfdfd;
}

.admin-sider--collapsed .brand {
  justify-content: center;
  padding: 0;
}

.admin-sider--collapsed .brand div {
  display: none;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 68px;
  padding: 0 18px;
}

.brand strong {
  display: block;
  color: #17212b;
  font-size: 17px;
  line-height: 1.2;
}

.brand span {
  color: #7a8794;
  font-size: 11px;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 68px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(12px);
}

.header-title {
  color: #18222d;
  font-size: 16px;
  font-weight: 750;
}

.header-subtitle {
  margin-top: 3px;
  color: #72808f;
  font-size: 12px;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-meta {
  display: flex;
  flex-direction: column;
}

.user-meta strong {
  color: #17212b;
  font-size: 13px;
}

.user-meta span {
  color: #7a8794;
  font-size: 11px;
}

.admin-content {
  min-height: calc(100vh - 68px);
  padding: 22px;
  background: #eef3f2;
}

@media (max-width: 760px) {
  .header-subtitle,
  .user-meta {
    display: none;
  }

  .admin-content {
    padding: 14px;
  }
}
</style>
