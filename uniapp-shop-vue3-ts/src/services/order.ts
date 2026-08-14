import type { OrderListResult } from '@/types/order'
import type {
  OrderCreateParams,
  OrderListParams,
  OrderLogisticResult,
  OrderPreResult,
  OrderResult,
} from '@/types/order'
import { http } from '@/utils/http'

/**
 * 填写订单-获取预付订单（购物车结算）
 * @description 从购物车选中的商品生成预览
 */
export const getMemberOrderPreAPI = () => {
  return http<OrderPreResult>({
    method: 'POST',
    url: '/orders/preview',
  })
}

/**
 * 填写订单-获取立即购买订单
 * @param data 商品信息（skuId + count）
 */
export const getMemberOrderPreNowAPI = (data: {
  skuId: string
  count: string
  addressId?: string
}) => {
  return http<OrderPreResult>({
    method: 'POST',
    url: '/orders/preview',
    data: {
      goods: [{ skuId: data.skuId, count: Number(data.count) }],
      addressId: data.addressId ? Number(data.addressId) : undefined,
    },
  })
}

/**
 * 填写订单-再次购买
 * @description 先获取订单详情，提取商品列表后调预览接口
 * @param id 订单id
 */
export const getMemberOrderRepurchaseByIdAPI = async (id: string) => {
  const detailRes = await http<OrderResult>({
    method: 'GET',
    url: `/orders/${id}`,
  })
  const goods = detailRes.data.skus.map((sku) => ({
    skuId: sku.skuId,
    count: sku.quantity,
  }))
  return http<OrderPreResult>({
    method: 'POST',
    url: '/orders/preview',
    data: { goods },
  })
}

/**
 * 提交订单
 * @param data 请求参数
 */
export const postMemberOrderAPI = (data: OrderCreateParams) => {
  return http<{ id: string }>({
    method: 'POST',
    url: '/orders',
    data,
  })
}

/**
 * 获取订单详情
 * @param id 订单id
 */
export const getMemberOrderByIdAPI = (id: string) => {
  return http<OrderResult>({
    method: 'GET',
    url: `/orders/${id}`,
  })
}

/**
 * 模拟发货-内测版
 * @description 后端尚未实现，DEV 环境下使用。调用后订单状态修改为待收货。
 * @param id 订单id
 */
export const getMemberOrderConsignmentByIdAPI = (id: string) => {
  return http({
    method: 'GET',
    url: `/orders/consignment/${id}`,
  })
}

/**
 * 确认收货
 * @description 仅在订单状态为待收货时，可确认收货。
 * @param id 订单id
 */
export const putMemberOrderReceiptByIdAPI = (id: string) => {
  return http<OrderResult>({
    method: 'PUT',
    url: `/orders/${id}/confirm`,
  })
}

/**
 * 标记已支付（Mock）
 * @description 待付款订单标记为已支付，状态流转为待发货。
 * @param id 订单id
 */
export const putMemberOrderPayAPI = (id: string) => {
  return http<OrderResult>({
    method: 'PUT',
    url: `/orders/${id}/pay`,
  })
}

/**
 * 获取订单物流
 * @description 仅在订单状态为待收货，待评价，已完成时，可获取物流信息。
 * @param id 订单id
 */
export const getMemberOrderLogisticsByIdAPI = (id: string) => {
  return http<OrderLogisticResult>({
    method: 'GET',
    url: `/orders/${id}/logistics`,
  })
}

/**
 * 删除订单
 * @description 仅在订单状态为待评价，已完成，已取消时，可删除订单。
 * @param id 订单id
 */
export const deleteMemberOrderAPI = (id: string) => {
  return http({
    method: 'DELETE',
    url: `/orders/${id}`,
  })
}

/**
 * 取消订单
 * @description 仅在订单状态为待付款时，可取消订单。
 * @param id 订单id
 * @param data cancelReason 取消理由
 */
export const getMemberOrderCancelByIdAPI = (id: string, data: { cancelReason: string }) => {
  return http<OrderResult>({
    method: 'PUT',
    url: `/orders/${id}/cancel`,
    data,
  })
}

/**
 * 获取订单列表
 * @param data orderState 订单状态
 */
export const getMemberOrderAPI = (data: OrderListParams) => {
  return http<OrderListResult>({
    method: 'GET',
    url: `/orders`,
    data,
  })
}
