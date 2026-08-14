import type { LoginResult } from '@/types/member'
import { http } from '@/utils/http'

/**
 * 微信小程序登录参数（对齐后端 WxLoginRequest）
 */
type LoginWxMinParams = {
  /** 微信 jscode，通过 wx.login() 获取 */
  code: string
  /** 昵称（可选，微信新规下用户填写后传入） */
  nickname?: string
  /** 头像 URL（可选） */
  avatar?: string
}

/**
 * 微信小程序登录
 *
 * <p>用 wx.login() 获取的 code 换取 openid，首次登录自动注册。
 * 微信 2024 年后 getUserProfile 已不可用，nickname/avatar 可不传，
 * 后端会给默认昵称"微信用户"。
 *
 * @param data 请求参数
 */
export const postLoginWxMinAPI = (data: LoginWxMinParams) => {
  return http<LoginResult>({
    method: 'POST',
    url: '/auth/wxLogin',
    data,
  })
}

type LoginParams = {
  phone: string
  password: string
}

/**
 * 密码登录（手机号 + 密码）
 * @param data 请求参数
 */
export const postLoginAPI = (data: LoginParams) => {
  return http<LoginResult>({
    method: 'POST',
    url: '/auth/login',
    data,
  })
}

type BindWechatPhoneParams = {
  /** 微信 openid（由 wxLogin 返回） */
  openid: string
  /** 手机号 */
  phone: string
}

/**
 * 微信新用户绑定手机号
 *
 * <p>微信首次登录后调用，通过手机号完成注册或合并已有账号。
 * 成功返回完整的 LoginResponse（含 token）。
 *
 * @param data 绑定请求参数
 */
export const postBindWechatPhoneAPI = (data: BindWechatPhoneParams) => {
  return http<LoginResult>({
    method: 'POST',
    url: '/auth/bindWechatPhone',
    data,
  })
}

type BindWechatPhoneByCodeParams = {
  /** 微信 openid（由 wxLogin 返回） */
  openid: string
  /**
   * getPhoneNumber 返回的加密凭证
   * （前端从 `<button open-type="getPhoneNumber">` 的 @getphonenumber 回调 e.detail.code 取）
   */
  phoneCode: string
}

/**
 * 微信新用户绑定手机号（phoneCode 形式）
 *
 * <p>前端在微信小程序中通过 `open-type="getPhoneNumber"` 拿到 phoneCode（加密凭证），
 * 由后端调微信 `getuserphonenumber` 接口解密为真实手机号，再走绑定/合并流程。
 * 要求小程序后台已开通「手机号快速验证」能力。
 *
 * @param data 绑定请求参数
 */
export const postBindWechatPhoneByCodeAPI = (data: BindWechatPhoneByCodeParams) => {
  return http<LoginResult>({
    method: 'POST',
    url: '/auth/bindWechatPhoneByCode',
    data,
  })
}
