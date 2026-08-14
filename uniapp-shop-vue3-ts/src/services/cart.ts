import type { CartItem } from '@/types/cart'
import { http } from '@/utils/http'

/**
 * 加入购物车
 * @param data 请求体参数
 */
export const postMemberCartAPI = (data: { skuId: string; count: number }) => {
  return http({
    method: 'POST',
    url: '/cart',
    data,
  })
}

/**
 * 获取购物车列表
 */
export const getMemberCartAPI = () => {
  return http<CartItem[]>({
    method: 'GET',
    url: '/cart',
  })
}

/**
 * 修改购物车单品数量
 * @param skuId SKU ID
 * @param data count 商品数量
 */
export const putMemberCartSkuCountAPI = (skuId: string, data: { count: number }) => {
  return http({
    method: 'PUT',
    url: `/cart/${skuId}`,
    data,
  })
}

/**
 * 选中/取消选中购物车单品
 * @param skuId SKU ID
 * @param data selected 选中状态
 */
export const putMemberCartSkuSelectedAPI = (skuId: string, data: { selected: boolean }) => {
  return http({
    method: 'PUT',
    url: `/cart/${skuId}/selected`,
    data,
  })
}

/**
 * 获取已选购物车列表（下单用）
 */
export const getMemberCartSelectedAPI = () => {
  return http<CartItem[]>({
    method: 'GET',
    url: '/cart/selected',
  })
}

/**
 * 购物车全选/取消全选
 * @param data selected 是否选中
 */
export const putMemberCartSelectedAPI = (data: { selected: boolean }) => {
  return http({
    method: 'PUT',
    url: '/cart/selected',
    data,
  })
}

/**
 * 删除购物车单品
 * @param skuId SKU ID
 */
export const deleteMemberCartAPI = (skuId: string) => {
  return http({
    method: 'DELETE',
    url: `/cart/${skuId}`,
  })
}

/**
 * 清空购物车
 */
export const clearMemberCartAPI = () => {
  return http({
    method: 'DELETE',
    url: '/cart',
  })
}
