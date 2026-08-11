import { http, normalizePage } from './http'
import type { AdminUser, EntityId, PageResult } from '@/types/admin'

export interface UserQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: number | null
  memberLevel?: string | null
}

export const listUsers = async (params: UserQuery) => {
  const page = await http.get<PageResult<AdminUser>, PageResult<AdminUser>>('/admin/users', { params })
  return normalizePage(page)
}

export const getUser = (id: EntityId) => http.get<AdminUser, AdminUser>(`/admin/users/${id}`)

export const updateUserStatus = (id: EntityId, status: number) =>
  http.put<void, void>(`/admin/users/${id}/status`, { status })

export const updateUserLevel = (id: EntityId, memberLevel: string) =>
  http.put<void, void>(`/admin/users/${id}/level`, { memberLevel })
