/**
 * 添加拦截器:
 *   拦截 request 请求
 *   拦截 uploadFile 文件上传
 *
 * TODO:
 *   1. 非 http 开头需拼接地址
 *   2. 请求超时
 *   3. 添加小程序端请求头标识
 *   4. 添加 token 请求头标识
 */

import { useMemberStore } from '@/stores'
import { isAuthLost } from '@/constants/resultCode'

// 从环境变量读取 API 基地址，未配置时回退到 localhost（开发环境）
const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

// 添加拦截器
const httpInterceptor = {
  // 拦截前触发
  invoke(options: UniApp.RequestOptions) {
    // 1. 非 http 开头需拼接地址
    if (!options.url.startsWith('http')) {
      options.url = baseURL + options.url
    }
    console.info('[request]', options.method || 'GET', options.url)
    // 2. 请求超时，仅在未显式设置时使用默认值（避免覆盖 AI 流式请求的 300s 等长超时）
    if (!options.timeout) {
      options.timeout = 60000
    }
    // 3. 添加小程序端请求头标识
    options.header = {
      ...options.header,
      'source-client': 'miniapp',
    }
    // 4. 添加 token 请求头标识
    const memberStore = useMemberStore()
    const token = memberStore.profile?.accessToken
    if (token) {
      options.header.Authorization = 'Bearer ' + token
    }
  },
}
uni.addInterceptor('request', httpInterceptor)
uni.addInterceptor('uploadFile', httpInterceptor)

/**
 * 请求函数
 * @param  UniApp.RequestOptions
 * @returns Promise
 *  1. 返回 Promise 对象
 *  2. 获取数据成功
 *    2.1 提取核心数据 res.data
 *    2.2 添加类型，支持泛型
 *  3. 获取数据失败
 *    3.1 401错误  -> 清理用户信息，跳转到登录页
 *    3.2 其他错误 -> 根据后端错误信息轻提示
 *    3.3 网络错误 -> 提示用户换网络
 */
type Data<T> = {
  code: number
  message: string
  data: T
}
// 2.2 添加类型，支持泛型
export const http = <T>(options: UniApp.RequestOptions) => {
  // 1. 返回 Promise 对象
  return new Promise<Data<T>>((resolve, reject) => {
    uni.request({
      ...options,
      // 响应成功
      success(res) {
        // 状态码 2xx
        if (res.statusCode >= 200 && res.statusCode < 300) {
          // 2.1 提取核心数据，并检查业务 code
          const body = res.data as Data<T>
          if (body && body.code === 200) {
            resolve(body)
          } else {
            // 业务异常（如 GlobalExceptionHandler 返回的非 200 的 code）
            const errMsg = body?.message || '请求错误'
            uni.showToast({ icon: 'none', title: errMsg })
            reject(res)
          }
        } else if (res.statusCode === 401) {
          // HTTP 401 → 读取 body.code 判断是"无登录态"还是"鉴权失败（如密码错误）"
          const body = res.data as Data<T>
          if (body && isAuthLost(body.code)) {
            // TOKEN_INVALID(1003) / UNAUTHORIZED(401) → 清理用户信息，跳转到登录页
            const memberStore = useMemberStore()
            memberStore.clearProfile()
            uni.reLaunch({ url: '/pages/login/login' })
          } else {
            // 密码错误等鉴权失败 → 仅 toast 提示，不清除登录态
            const errMsg = body?.message || '认证失败'
            uni.showToast({ icon: 'none', title: errMsg })
          }
          reject(res)
        } else {
          // 其他错误 -> 根据后端错误信息轻提示
          // 后端非标准错误（HTML 错误页、网关 502）时 res.data 可能不是 JSON 形状，
          // 用 ?? {} 兜底避免运行时报错
          const errData = (res.data ?? {}) as Data<T>
          uni.showToast({
            icon: 'none',
            title: errData.message || '请求错误',
          })
          reject(res)
        }
      },
      // 响应失败
      fail(err) {
        console.error('[request:fail]', options.url, err)
        uni.showToast({
          icon: 'none',
          title: '网络错误，换个网络试试',
        })
        reject(err)
      },
    })
  })
}
