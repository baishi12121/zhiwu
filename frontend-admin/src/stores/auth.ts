import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getProfile, login as loginApi, logout as logoutApi } from '@/api/auth'
import { TOKEN_KEY } from '@/api/http'
import type { AdminLoginPayload, AdminLoginResponse, AdminUser } from '@/types/admin'

const readStoredAuth = () => {
  const raw = localStorage.getItem(TOKEN_KEY)
  return raw ? (JSON.parse(raw) as AdminLoginResponse) : null
}

export const useAuthStore = defineStore('auth', () => {
  const auth = ref<AdminLoginResponse | null>(readStoredAuth())
  const profile = ref<AdminUser | null>(null)
  const isLoggedIn = computed(() => Boolean(auth.value?.accessToken))

  const login = async (payload: AdminLoginPayload) => {
    auth.value = await loginApi(payload)
    localStorage.setItem(TOKEN_KEY, JSON.stringify(auth.value))
    profile.value = await getProfile()
  }

  const loadProfile = async () => {
    if (!isLoggedIn.value) return
    profile.value = await getProfile()
  }

  const logoutLocal = () => {
    auth.value = null
    profile.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  const logout = async () => {
    try {
      await logoutApi()
    } finally {
      logoutLocal()
    }
  }

  return { auth, profile, isLoggedIn, login, loadProfile, logout, logoutLocal }
})
