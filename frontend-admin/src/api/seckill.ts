import { http, normalizePage } from './http'
import type { EntityId, PageResult, SeckillActivity, SeckillItem } from '@/types/admin'

export interface SeckillActivityQuery {
  page: number
  pageSize: number
  enabled?: number | null
}

export const listActivities = async (params: SeckillActivityQuery) => {
  const page = await http.get<PageResult<SeckillActivity>, PageResult<SeckillActivity>>(
    '/admin/seckill/activities',
    { params },
  )
  return normalizePage(page)
}

export const getActivity = (id: EntityId) =>
  http.get<SeckillActivity, SeckillActivity>(`/admin/seckill/activities/${id}`)

export const createActivity = (payload: Partial<SeckillActivity>) =>
  http.post<number, number>('/admin/seckill/activities', payload)

export const updateActivity = (id: EntityId, payload: Partial<SeckillActivity>) =>
  http.put<void, void>(`/admin/seckill/activities/${id}`, payload)

export const updateActivityEnabled = (id: EntityId, status: number) =>
  http.put<void, void>(`/admin/seckill/activities/${id}/enabled`, { status })

export const deleteActivity = (id: EntityId) =>
  http.delete<void, void>(`/admin/seckill/activities/${id}`)

export const listItems = (activityId: EntityId) =>
  http.get<SeckillItem[], SeckillItem[]>(`/admin/seckill/activities/${activityId}/items`)

export const addItem = (activityId: EntityId, payload: Partial<SeckillItem>) =>
  http.post<number, number>(`/admin/seckill/activities/${activityId}/items`, payload)

export const updateItem = (itemId: EntityId, payload: Partial<SeckillItem>) =>
  http.put<void, void>(`/admin/seckill/items/${itemId}`, payload)

export const updateItemStatus = (itemId: EntityId, status: number) =>
  http.put<void, void>(`/admin/seckill/items/${itemId}/status`, { status })

export const deleteItem = (itemId: EntityId) => http.delete<void, void>(`/admin/seckill/items/${itemId}`)
