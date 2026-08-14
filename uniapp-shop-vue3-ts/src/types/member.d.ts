/** 通用的用户信息 */
type BaseProfile = {
  /** 用户ID */
  id: number
  /** 头像  */
  avatar: string
  /** 账户名  */
  account: string
  /** 昵称 */
  nickname?: string
}

/** 小程序登录 登录用户信息（对齐后端 LoginResponse） */
export type LoginResult = {
  /** 用户ID */
  userId: number
  /** 昵称 */
  nickname: string
  /** 头像 URL */
  avatar: string
  /** 会员等级 */
  memberLevel: string
  /** 访问令牌 */
  accessToken: string
  /** 刷新令牌 */
  refreshToken: string
  /** access token 有效期（秒） */
  expiresIn: number
  /** 是否需要绑定手机号（微信新用户首次登录为 true） */
  needBindPhone?: boolean
  /** 微信 openid（needBindPhone=true 时返回，用于绑定手机号） */
  openid?: string
  /** 账户名（可选） */
  account?: string
  /** 手机号（可选） */
  mobile?: string
}

/** 个人信息 用户详情信息 */
export type ProfileDetail = BaseProfile & {
  /** 性别 */
  gender?: Gender
  /** 生日 */
  birthday?: string
  /** 省市区 */
  fullLocation?: string
  /** 省份编码 */
  provinceCode?: string
  /** 城市编码 */
  cityCode?: string
  /** 区/县编码 */
  countyCode?: string
  /** 职业 */
  profession?: string
}
/** 性别 */
export type Gender = '女' | '男'

/** 个人信息 修改请求体参数 */
export type ProfileParams = Pick<
  ProfileDetail,
  'nickname' | 'gender' | 'birthday' | 'profession'
> & {
  /** 省份编码 */
  provinceCode?: string
  /** 城市编码 */
  cityCode?: string
  /** 区/县编码 */
  countyCode?: string
}
