/**
 * 业务结果码常量（与后端 ResultCode 枚举同步）。
 *
 * 后端定义见 mall-common-core/.../ResultCode.java。
 * 当后端新增业务错误码时，请同步更新此文件。
 */

/** HTTP 通用码 */
export const SUCCESS = 200
export const BAD_REQUEST = 400
export const UNAUTHORIZED = 401
export const FORBIDDEN = 403
export const NOT_FOUND = 404
export const TOO_MANY_REQUESTS = 429
export const INTERNAL_ERROR = 500

/** 用户域 1xxx */
export const USER_AUTH_FAILED = 1001
export const USER_EXISTS = 1002
export const TOKEN_INVALID = 1003

/** 商品域 2xxx */
export const PRODUCT_STOCK_NOT_ENOUGH = 2001
export const PRODUCT_OFFLINE = 2002

/** 优惠券域 3xxx */
export const COUPON_SOLD_OUT = 3001
export const COUPON_DUPLICATE_GRAB = 3002
export const COUPON_USED = 3003

/** 订单域 4xxx */
export const ORDER_STATUS_ILLEGAL = 4001

/**
 * 判断业务 code 是否为"无登录态"（需要清理本地 token 并跳转登录页）。
 * 仅 TOKEN_INVALID(1003) 和 UNAUTHORIZED(401) 属于此类；
 * 密码错误(1001) 等鉴权失败不在此列，不应清理已登录的 token。
 */
export function isAuthLost(code: number): boolean {
  return code === UNAUTHORIZED || code === TOKEN_INVALID
}
