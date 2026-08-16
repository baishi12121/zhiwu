<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { LockClosedOutline, PersonOutline } from '@vicons/ionicons5'
import ShopIcon from '@/components/ShopIcon.vue'
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
    <!-- 左侧视觉区 -->
    <section class="login-visual">
      <div class="login-brand">
        <span class="brand-icon">
          <ShopIcon :size="22" />
        </span>
        <div class="brand-text">
          <strong>知物商城</strong>
          <span>Admin Console</span>
        </div>
      </div>

      <div class="login-hero">
        <h1>把商品、活动和用户运营<br />放在同一个清晰工作台。</h1>
        <p>对接 mall-admin-service，支持销售总览、商品上下架、秒杀配置和用户状态管理。</p>
      </div>

      <div class="signal-grid">
        <div class="signal-card">
          <strong>SPU / SKU</strong>
          <span>库存与价格维护</span>
        </div>
        <div class="signal-card">
          <strong>Seckill</strong>
          <span>活动与商品编排</span>
        </div>
        <div class="signal-card">
          <strong>Sales</strong>
          <span>趋势与排行分析</span>
        </div>
      </div>
    </section>

    <!-- 右侧登录卡片 -->
    <section class="login-card-wrap">
      <div class="login-card">
        <div class="login-card-head">
          <h2>管理员登录</h2>
          <p>使用后台管理员账号进入控制台</p>
        </div>

        <n-form :model="form" :rules="rules" size="large" @submit.prevent="submit">
          <n-form-item path="account">
            <n-input v-model:value="form.account" placeholder="账号" :input-props="{ autocomplete: 'username' }">
              <template #prefix>
                <n-icon><PersonOutline /></n-icon>
              </template>
            </n-input>
          </n-form-item>
          <n-form-item path="password">
            <n-input
              v-model:value="form.password"
              type="password"
              show-password-on="click"
              placeholder="密码"
              :input-props="{ autocomplete: 'current-password' }"
              @keyup.enter="submit"
            >
              <template #prefix>
                <n-icon><LockClosedOutline /></n-icon>
              </template>
            </n-input>
          </n-form-item>
          <n-button type="primary" block size="large" :loading="loading" @click="submit">登录后台</n-button>
        </n-form>

        <div class="login-foot">
          <span>知物商城后台 · 仅限授权人员访问</span>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 440px;
  min-height: 100vh;
  background: var(--color-bg);
}

/* —— 左侧视觉区 —— */
.login-visual {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 48px;
  padding: 6vw 8vw;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  position: relative;
  overflow: hidden;
}

.login-visual::before {
  content: '';
  position: absolute;
  top: -120px;
  right: -120px;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  background: var(--color-primary-subtle);
  opacity: 0.5;
  pointer-events: none;
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
}

.brand-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
  border-radius: var(--radius-card);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-text strong {
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: var(--font-weight-semibold);
}

.brand-text span {
  color: var(--color-text-tertiary);
  font-size: 12px;
  letter-spacing: 0.4px;
}

.login-hero {
  position: relative;
}

.login-hero h1 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 44px;
  font-weight: var(--font-weight-bold);
  line-height: 1.15;
  letter-spacing: -0.4px;
}

.login-hero p {
  max-width: 520px;
  margin: 20px 0 0;
  color: var(--color-text-secondary);
  font-size: 15px;
  line-height: 1.75;
}

/* —— 能力卡片 —— */
.signal-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  max-width: 680px;
  position: relative;
}

.signal-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
}

.signal-card strong {
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: var(--font-weight-semibold);
}

.signal-card span {
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

/* —— 右侧登录区 —— */
.login-card-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--color-bg);
}

.login-card {
  width: 100%;
  max-width: 380px;
  padding: 36px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: 0 8px 32px rgba(15, 23, 42, 0.04);
}

.login-card-head {
  margin-bottom: 28px;
}

.login-card-head h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 24px;
  font-weight: var(--font-weight-semibold);
  line-height: 1.3;
}

.login-card-head p {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.login-foot {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
  text-align: center;
}

.login-foot span {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

/* —— Responsive —— */
@media (max-width: 980px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-visual {
    padding: 32px 24px;
    border-right: none;
    border-bottom: 1px solid var(--color-border);
  }

  .login-visual::before {
    display: none;
  }

  .login-hero h1 {
    font-size: 28px;
  }

  .signal-grid {
    grid-template-columns: 1fr;
    max-width: none;
  }

  .login-card-wrap {
    padding: 24px;
  }
}

@media (max-width: 480px) {
  .login-card {
    padding: 24px;
  }

  .login-hero h1 {
    font-size: 24px;
  }
}
</style>
