import axios, { type AxiosError } from 'axios'
import type { ApiResult, PageResult } from '@/types/admin'

const TOKEN_KEY = 'zhiwu-admin-auth'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const raw = localStorage.getItem(TOKEN_KEY)
  if (raw) {
    const auth = JSON.parse(raw) as { accessToken?: string }
    if (auth.accessToken) {
      config.headers.Authorization = `Bearer ${auth.accessToken}`
    }
  }
  config.headers['source-client'] = 'admin-web'
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>
    if (!body || typeof body.code !== 'number') {
      return response.data
    }
    if (body.code === 200) {
      return body.data
    }
    const error = new Error(body.message || '请求失败')
    Object.assign(error, { code: body.code })
    throw error
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    const code = error.response?.data?.code || error.response?.status
    if (code === 401 || code === 1003) {
      localStorage.removeItem(TOKEN_KEY)
      if (location.pathname !== '/login') {
        location.assign('/login')
      }
    }
    const message = error.response?.data?.message || error.message || '网络请求失败'
    throw new Error(message)
  },
)

export const normalizePage = <T>(page: PageResult<T>) => ({
  total: Number(page.counts ?? page.total ?? 0),
  page: Number(page.page ?? 1),
  pageSize: Number(page.pageSize ?? 10),
  items: page.items ?? page.list ?? [],
})

export { TOKEN_KEY }
