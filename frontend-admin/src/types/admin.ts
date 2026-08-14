export type EntityId = string | number

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  counts?: number
  total?: number
  page: number
  pages?: number
  pageSize: number
  items?: T[]
  list?: T[]
}

export interface AdminLoginPayload {
  account: string
  password: string
}

export interface AdminLoginResponse {
  userId: EntityId
  nickname: string
  avatar?: string
  memberLevel?: string
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface BaseEntity {
  id: EntityId
  createTime?: string
  updateTime?: string
}

export interface AdminCategory {
  id: EntityId
  parentId?: EntityId
  name: string
  icon?: string
  picture?: string
  sortOrder?: number
  status?: number
}

export interface AdminBanner extends BaseEntity {
  title: string
  imgUrl: string
  hrefUrl?: string
  type: number
  distributionSite: number
  sortOrder?: number
  status?: number
  startTime?: string
  endTime?: string
}

export interface AdminProduct extends BaseEntity {
  categoryId: EntityId
  brandId?: EntityId
  spuCode?: string
  name: string
  subtitle?: string
  description?: string
  price: number
  oldPrice?: number
  discount?: number
  inventory?: number
  salesCount?: number
  commentCount?: number
  collectCount?: number
  isPreSale?: number
  status?: number
  skus?: AdminProductSku[]
  images?: AdminProductImage[]
  properties?: AdminProductProperty[]
}

export interface AdminProductSku extends BaseEntity {
  productId?: EntityId
  skuCode?: string
  price: number
  oldPrice?: number
  inventory?: number
  picture?: string
  status?: number
}

export interface AdminProductImage {
  imageType?: number
  imageUrl: string
  sortOrder?: number
}

export interface AdminProductProperty {
  name: string
  value: string
  sortOrder?: number
}

export interface ProductSaveRequest {
  categoryId?: EntityId
  brandId?: EntityId
  spuCode?: string
  name: string
  subtitle?: string
  description?: string
  price?: number
  oldPrice?: number
  discount?: number
  inventory?: number
  status?: number
  isPreSale?: number
  skus?: Array<Partial<AdminProductSku>>
  images?: AdminProductImage[]
  properties?: AdminProductProperty[]
}

export interface StockAdjustRequest {
  inventory: number
  absolute?: boolean
  limit?: number
}

export interface AdminUser extends BaseEntity {
  account?: string
  nickname?: string
  mobile?: string
  avatar?: string
  gender?: number
  birthday?: string
  profession?: string
  balance?: number
  memberLevel?: string
  isAdmin?: number
  growth?: number
  status?: number
  lastLoginAt?: string
}

export interface SeckillActivity extends BaseEntity {
  name: string
  startTime: string
  endTime: string
  enabled?: number
  remark?: string
  itemCount?: number
}

export interface SeckillItem extends BaseEntity {
  activityId?: EntityId
  spuId: EntityId
  skuId: EntityId
  seckillPrice: number
  seckillStock: number
  limitPerUser?: number
  sortOrder?: number
  status?: number
  spuName?: string
  skuCode?: string
  originalPrice?: number
}

export interface SalesOverview {
  totalOrders?: number
  totalSales?: number
  totalAmount?: number
  totalUsers?: number
  [key: string]: unknown
}

export interface AdminOrder extends BaseEntity {
  orderNo: string
  userId: EntityId
  nickname?: string
  orderState: number
  orderSource: number
  totalMoney?: number
  payMoney: number
  postFee?: number
  discountAmount?: number
  payType?: number
  payChannel?: number
  deliveryTimeType?: number
  buyerMessage?: string
  receiverContact?: string
  receiverMobile?: string
  receiverAddress?: string
  cancelReason?: string
  payLatestTime?: string
  paidAt?: string
  shippedAt?: string
  receivedAt?: string
  completedAt?: string
  cancelledAt?: string
  activityId?: EntityId
  seckillItemId?: EntityId
  itemImage?: string
  itemName?: string
  itemCount?: number
  totalNum?: number
  items?: AdminOrderItem[]
  statusLogs?: AdminOrderStatusLog[]
  logistics?: OrderLogistics | null
}

export interface AdminOrderItem extends BaseEntity {
  orderId: EntityId
  skuId?: EntityId
  spuId: EntityId
  skuCode?: string
  name: string
  image?: string
  attrsText?: string
  curPrice?: number
  price?: number
  quantity: number
  subtotal?: number
  realPay?: number
  properties?: string
}

export interface AdminOrderStatusLog extends BaseEntity {
  orderId: EntityId
  fromState?: number
  toState: number
  operator?: 'USER' | 'SYSTEM' | 'ADMIN' | string
  remark?: string
}

export interface LogisticsCompany {
  id: EntityId
  name: string
  code?: string
  tel?: string
  sortOrder?: number
}

export interface OrderLogistics {
  id: EntityId
  orderId: EntityId
  companyId?: EntityId
  companyName?: string
  companyCode?: string
  companyTel?: string
  logisticsNo?: string
  createTime?: string
  track?: OrderLogisticsTrack[]
}

export interface OrderLogisticsTrack {
  id: EntityId
  orderLogisticsId: EntityId
  content: string
  occurTime: string
  sortOrder?: number
}
