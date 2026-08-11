import { http } from './http'
import type { SalesOverview } from '@/types/admin'

export const getSalesOverview = () => http.get<SalesOverview, SalesOverview>('/admin/sales/overview')

export const getProductRanking = (limit = 10) =>
  http.get<Array<Record<string, unknown>>, Array<Record<string, unknown>>>('/admin/sales/products', {
    params: { limit },
  })

export const getCategoryDistribution = () =>
  http.get<Array<Record<string, unknown>>, Array<Record<string, unknown>>>('/admin/sales/categories')

export const getDailyTrend = (startDate?: string, endDate?: string) =>
  http.get<Array<Record<string, unknown>>, Array<Record<string, unknown>>>('/admin/sales/trend/daily', {
    params: { startDate, endDate },
  })
