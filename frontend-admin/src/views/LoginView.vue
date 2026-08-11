<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { LockClosedOutline, PersonOutline, StorefrontOutline } from '@vicons/ionicons5'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const auth = useAuthStore()
const loading = ref(false)

const form = reactive({
  account: '',
  password: '',
})

const rules = {
  account: { required: true, message: '请输入管理员账号', trigger: 'blur' },
  password: { required: true, min: 6, message: '请输入 6-32 位密码', trigger: 'blur' },
}

const submit = async () => {
  loading.value = true
  try {
    await auth.login(form)
    message.success('登录成功')
    router.replace((route.query.redirect as string) || '/dashboard')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-visual">
      <div class="login-brand">
        <n-icon size="36"><StorefrontOutline /></n-icon>
        <span>植屋商城 Admin</span>
      </div>
      <h1>把商品、活动和用户运营放在同一个清晰工作台。</h1>
      <p>对接 mall-admin-service，支持销售总览、商品上下架、秒杀配置和用户状态管理。</p>
      <div class="signal-grid">
        <div><strong>SPU/SKU</strong><span>库存与价格维护</span></div>
        <div><strong>Seckill</strong><span>活动与商品编排</span></div>
        <div><strong>Sales</strong><span>趋势与排行分析</span></div>
      </div>
    </section>
    <section class="login-card">
      <h2>管理员登录</h2>
      <p>使用后台管理员账号进入控制台</p>
      <n-form :model="form" :rules="rules" size="large" @submit.prevent="submit">
        <n-form-item path="account">
          <n-input v-model:value="form.account" placeholder="账号">
            <template #prefix>
              <n-icon><PersonOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>
        <n-form-item path="password">
          <n-input v-model:value="form.password" type="password" show-password-on="click" placeholder="密码">
            <template #prefix>
              <n-icon><LockClosedOutline /></n-icon>
            </template>
          </n-input>
        </n-form-item>
        <n-button type="primary" block size="large" :loading="loading" @click="submit">登录后台</n-button>
      </n-form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  min-height: 100vh;
  background:
    radial-gradient(circle at 10% 20%, rgba(20, 184, 166, 0.18), transparent 30%),
    linear-gradient(135deg, #eaf5f1 0%, #f7faf9 58%, #e7edf6 100%);
}

.login-visual {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 8vw;
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #0f766e;
  font-size: 18px;
  font-weight: 750;
}

h1 {
  max-width: 740px;
  margin: 42px 0 18px;
  color: #111827;
  font-size: 52px;
  font-weight: 800;
  line-height: 1.08;
}

.login-visual p {
  max-width: 560px;
  margin: 0;
  color: #536271;
  font-size: 16px;
  line-height: 1.8;
}

.signal-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  max-width: 680px;
  margin-top: 42px;
}

.signal-grid div {
  min-height: 92px;
  padding: 16px;
  border: 1px solid rgba(15, 118, 110, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.66);
}

.signal-grid strong,
.signal-grid span {
  display: block;
}

.signal-grid strong {
  color: #0f766e;
  font-size: 18px;
}

.signal-grid span {
  margin-top: 8px;
  color: #637083;
  font-size: 13px;
}

.login-card {
  align-self: center;
  margin-right: 6vw;
  padding: 34px;
  border: 1px solid #dbe5e5;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 24px 70px rgba(26, 50, 60, 0.12);
}

.login-card h2 {
  margin: 0;
  color: #111827;
  font-size: 28px;
}

.login-card p {
  margin: 8px 0 28px;
  color: #687789;
}

@media (max-width: 980px) {
  .login-page {
    grid-template-columns: 1fr;
    padding: 20px;
  }

  .login-visual {
    padding: 28px 8px;
  }

  h1 {
    font-size: 34px;
  }

  .signal-grid {
    grid-template-columns: 1fr;
  }

  .login-card {
    width: 100%;
    margin: 0;
  }
}
</style>
