import { http, normalizePage } from './http'
import type { AdminOrder, EntityId, LogisticsCompany, PageResult } from '@/types/admin'

export interface AdminOrderQuery {
  page: number
  pageSize: number
  orderState?: number | null
  orderSource?: number | null
  keyword?: string
  start?: string
  end?: string
}

export const listOrders = async (params: AdminOrderQuery) => {
  const page = await http.get<PageResult<AdminOrder>, PageResult<AdminOrder>>('/admin/orders', { params })
  return normalizePage(page)
}

export const getOrder = (id: EntityId) => http.get<AdminOrder, AdminOrder>(`/admin/orders/${id}`)

export const shipOrder = (id: EntityId, payload: { companyId: EntityId; logisticsNo: string }) =>
  http.put<AdminOrder, AdminOrder>(`/admin/orders/${id}/ship`, payload)

export const listLogisticsCompanies = () =>
  http.get<LogisticsCompany[], LogisticsCompany[]>('/admin/orders/logistics/companies')
