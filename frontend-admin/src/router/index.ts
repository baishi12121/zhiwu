import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: 'products', name: 'products', component: () => import('@/views/ProductsView.vue') },
        { path: 'banners', name: 'banners', component: () => import('@/views/BannersView.vue') },
        { path: 'seckill', name: 'seckill', component: () => import('@/views/SeckillView.vue') },
        { path: 'users', name: 'users', component: () => import('@/views/UsersView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue') },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && auth.isLoggedIn) {
    return '/dashboard'
  }
})

export default router
