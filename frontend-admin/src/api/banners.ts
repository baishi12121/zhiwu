import { http, normalizePage } from './http'
import type { AdminBanner, EntityId, PageResult } from '@/types/admin'

export interface BannerQuery {
  page: number
  pageSize: number
  distributionSite?: number | null
  status?: number | null
}

export const listBanners = async (params: BannerQuery) => {
  const page = await http.get<PageResult<AdminBanner>, PageResult<AdminBanner>>('/admin/banners', { params })
  return normalizePage(page)
}

export const getBanner = (id: EntityId) => http.get<AdminBanner, AdminBanner>(`/admin/banners/${id}`)

export const createBanner = (payload: Partial<AdminBanner>) =>
  http.post<number, number>('/admin/banners', payload)

export const updateBanner = (id: EntityId, payload: Partial<AdminBanner>) =>
  http.put<void, void>(`/admin/banners/${id}`, payload)

export const updateBannerStatus = (id: EntityId, status: number) =>
  http.put<void, void>(`/admin/banners/${id}/status`, { status })

export const deleteBanner = (id: EntityId) => http.delete<void, void>(`/admin/banners/${id}`)
