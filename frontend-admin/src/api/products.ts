import { http, normalizePage } from './http'
import type {
  AdminProduct,
  AdminProductSku,
  EntityId,
  PageResult,
  ProductSaveRequest,
  StockAdjustRequest,
} from '@/types/admin'

export interface ProductQuery {
  page: number
  pageSize: number
  categoryId?: EntityId | null
  keyword?: string
  status?: number | null
}

export const listProducts = async (params: ProductQuery) => {
  const page = await http.get<PageResult<AdminProduct>, PageResult<AdminProduct>>('/admin/products', { params })
  return normalizePage(page)
}

export const getProduct = (id: EntityId) => http.get<AdminProduct, AdminProduct>(`/admin/products/${id}`)

export const createProduct = (payload: ProductSaveRequest) =>
  http.post<number, number>('/admin/products', payload)

export const updateProduct = (id: EntityId, payload: ProductSaveRequest) =>
  http.put<void, void>(`/admin/products/${id}`, payload)

export const updateProductStatus = (id: EntityId, status: number) =>
  http.put<void, void>(`/admin/products/${id}/status`, { status })

export const adjustProductStock = (id: EntityId, payload: StockAdjustRequest) =>
  http.put<void, void>(`/admin/products/${id}/stock`, payload)

export const deleteProduct = (id: EntityId) => http.delete<void, void>(`/admin/products/${id}`)

export const listSkus = (productId: EntityId) =>
  http.get<AdminProductSku[], AdminProductSku[]>(`/admin/products/${productId}/skus`)

export const addSku = (productId: EntityId, payload: Partial<AdminProductSku>) =>
  http.post<number, number>(`/admin/products/${productId}/skus`, payload)

export const updateSku = (skuId: EntityId, payload: Partial<AdminProductSku>) =>
  http.put<void, void>(`/admin/skus/${skuId}`, payload)

export const updateSkuStatus = (skuId: EntityId, status: number) =>
  http.put<void, void>(`/admin/skus/${skuId}/status`, { status })

export const adjustSkuStock = (skuId: EntityId, payload: StockAdjustRequest) =>
  http.put<void, void>(`/admin/skus/${skuId}/stock`, payload)

export const deleteSku = (skuId: EntityId) => http.delete<void, void>(`/admin/skus/${skuId}`)

export interface UploadResponse {
  url: string
}

export const uploadFile = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<UploadResponse, UploadResponse>('/upload', formData)
}
