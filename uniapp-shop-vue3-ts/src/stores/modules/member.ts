import type { LoginResult } from '@/types/member'
import { defineStore } from 'pinia'
import { ref } from 'vue'

// 定义 Store
export const useMemberStore = defineStore(
  'member',
  () => {
    // 会员信息
    const profile = ref<LoginResult>()

    // 保存会员信息，登录时使用
    const setProfile = (val: LoginResult) => {
      profile.value = val
    }

    // 清理会员信息，退出时使用
    const clearProfile = () => {
      profile.value = undefined
    }

    // 记得 return
    return {
      profile,
      setProfile,
      clearProfile,
    }
  },
  {
    // 网页端配置
    // persist: true,
    // 小程序端配置
    persist: {
      storage: {
        getItem(key) {
          try {
            const value = uni.getStorageSync(key)
            // 微信 getStorageSync 对不存在的 key 返回空字符串，pinia 插件需要 null
            return value === '' ? null : value
          } catch {
            return null
          }
        },
        setItem(key, value) {
          try {
            uni.setStorageSync(key, value)
          } catch {
            // 存储空间满等情况静默失败，内存中的状态仍然有效
          }
        },
      },
    },
  },
)
