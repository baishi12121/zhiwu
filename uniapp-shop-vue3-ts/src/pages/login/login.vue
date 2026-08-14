<script setup lang="ts">
import { postBindWechatPhoneAPI, postLoginAPI, postLoginWxMinAPI } from '@/services/login'
import { putMemberProfileAPI } from '@/services/profile'
import { useMemberStore } from '@/stores'
import type { LoginResult } from '@/types/member'
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'

/** 默认头像（弹窗和资料完善页共用） */
const defaultAvatar =
  'https://yjy-xiaotuxian-dev.oss-cn-beijing.aliyuncs.com/picture/2021-04-06/db628d42-88a7-46e7-abb8-659448c33081.png'

/** 应用 logo（申请手机号 sheet header 用） */
const appLogo = 'https://pcapi-xiaotuxian-front-devtest.itheima.net/miniapp/images/logo_icon.png'

// ==================== 微信登录（小程序） ====================
// #ifdef MP-WEIXIN
// 微信 jscode，onLoad 时预取
let wxCode = ''

onLoad(async () => {
  try {
    const res = await wx.login()
    wxCode = res.code
    console.log('[login] wx.login 成功，code=', wxCode)
  } catch (e) {
    console.error('[login] wx.login 失败', e)
  }
})

/** 微信一键登录 —— 先调 wxLogin 拿真实 mobile（中间四位隐藏展示），再弹"申请手机号" sheet */
const onWechatLogin = async () => {
  // 每次点击都重新 wx.login()，避免使用已过期的 code（有效期仅约 5 分钟）
  try {
    const loginRes = await wx.login()
    wxCode = loginRes.code
    console.log('[login] wx.login 成功，code=', wxCode)
  } catch (e) {
    console.error('[login] wx.login 失败', e)
    uni.showToast({ icon: 'none', title: '微信登录失败，请重试' })
    return
  }

  // 调 wxLogin 拿 openid + mobile
  uni.showLoading({ title: '加载中...', mask: true })
  try {
    const res = await postLoginWxMinAPI({ code: wxCode })
    wxLoginResult = res.data

    // 如果后端直接签发了 token（兼容老接口），跳过手机号绑定直接登录
    if (res.data.accessToken) {
      uni.hideLoading()
      loginSuccess(res.data)
      return
    }

    // 预填已有手机号（老用户），新用户为空需要手动输入
    phoneInput.value = res.data.mobile || ''
    uni.hideLoading()
    // 弹出绑定手机号 sheet
    showAuthSheet.value = true
  } catch (e: any) {
    uni.hideLoading()
    const errMsg = e?.data?.message || e?.message || e?.errMsg || '微信登录失败'
    console.error('[login] wxLogin 接口失败:', errMsg, e)
  }
}

// ==================== 绑定手机号 Sheet ====================

/** 是否显示绑定手机号 sheet */
const showAuthSheet = ref(false)

/** 默认用户名（弹窗 header 显示用） */
const defaultAppName = '小兔鲜儿+'

/** 用户输入的手机号（预填 wxLogin 返回的已有手机号，新用户为空） */
const phoneInput = ref('')

/** 缓存 wxLogin 结果（含 openid），供提交绑定时复用 */
let wxLoginResult: LoginResult | null = null

/** 关闭绑定 sheet */
const onCloseAuthSheet = () => {
  showAuthSheet.value = false
  phoneInput.value = ''
}

/**
 * "允许" 按钮：校验手机号 → 调 bindWechatPhone 完成绑定并自动登录。
 *
 * <p>不再依赖微信 getPhoneNumber 组件（需要小程序后台开通手机号验证能力），
 * 直接由用户输入手机号完成绑定。
 */
const onAllowBindPhone = async () => {
  const phone = phoneInput.value.trim()
  if (!/^1\d{10}$/.test(phone)) {
    uni.showToast({ icon: 'none', title: '请输入正确的手机号' })
    return
  }

  const openid = wxLoginResult?.openid
  if (!openid) {
    uni.showToast({ icon: 'none', title: '登录态失效，请重新进入' })
    return
  }

  // 关闭 sheet
  showAuthSheet.value = false

  uni.showLoading({ title: '登录中...', mask: true })
  try {
    const bindRes = await postBindWechatPhoneAPI({ openid, phone })
    phoneInput.value = ''
    loginSuccess(bindRes.data)
    uni.hideLoading()
  } catch (e: any) {
    const errMsg = e?.data?.message || e?.message || e?.errMsg || '登录失败'
    console.error('[login] bindWechatPhone 失败:', errMsg, e)
    uni.hideLoading()
  }
}

// ==================== 微信绑定手机号 ====================

/** 是否展示手机号绑定界面 */
const showBindPhone = ref(false)

/** 微信 openid（wxLogin 返回，绑定手机号时需要） */
const bindOpenid = ref('')

/** 绑定手机号输入 */
const bindPhone = ref('')

/** 提交绑定手机号 */
const onBindPhone = async () => {
  if (!bindPhone.value.trim()) {
    uni.showToast({ icon: 'none', title: '请输入手机号' })
    return
  }
  // 简单校验 11 位手机号
  if (!/^1\d{10}$/.test(bindPhone.value.trim())) {
    uni.showToast({ icon: 'none', title: '请输入正确的手机号' })
    return
  }

  uni.showLoading({ title: '绑定中...', mask: true })

  try {
    const res = await postBindWechatPhoneAPI({
      openid: bindOpenid.value,
      phone: bindPhone.value.trim(),
    })
    // 先保存登录态到 Store（后续上传头像和更新资料需要 token）
    const memberStore = useMemberStore()
    memberStore.setProfile(res.data)
    // 进入资料完善步骤
    showBindPhone.value = false
    showProfileCompletion.value = true
  } catch (e) {
    // 错误信息已在 http 拦截器中统一 toast 提示
  } finally {
    uni.hideLoading()
  }
}

/** 取消绑定，返回微信登录首页 */
const onCancelBind = () => {
  showBindPhone.value = false
  bindOpenid.value = ''
  bindPhone.value = ''
}

// ==================== 微信新用户资料完善 ====================

/** 是否展示资料完善界面（绑定手机号后） */
const showProfileCompletion = ref(false)

/** 用户选择的头像临时路径 */
const chosenAvatarUrl = ref('')

/** 用户输入的昵称 */
const chosenNickname = ref('')

/** 选择头像（微信 chooseAvatar 回调） */
const onChooseAvatar = (e: any) => {
  chosenAvatarUrl.value = e.detail.avatarUrl
}

/** 上传头像到服务器，返回永久 URL */
const uploadAvatarToServer = (tempUrl: string): Promise<string> => {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: '/user/profile/avatar',
      name: 'file',
      filePath: tempUrl,
      success: (res) => {
        if (res.statusCode === 200) {
          try {
            const data = JSON.parse(res.data)
            // 双重校验：HTTP 200 + 业务 code 200，避免后端异常被静默吞掉
            if (data.code === 200) {
              resolve(data.data.avatar)
            } else {
              reject(new Error(data.message || '上传失败'))
            }
          } catch {
            reject(new Error('解析上传结果失败'))
          }
        } else {
          reject(new Error('上传失败'))
        }
      },
      fail: reject,
    })
  })
}

/** 提交资料完善 */
const onSubmitProfile = async () => {
  uni.showLoading({ title: '保存中...', mask: true })
  try {
    // 1. 有选择头像则先上传
    if (chosenAvatarUrl.value) {
      const permanentUrl = await uploadAvatarToServer(chosenAvatarUrl.value)
      const store = useMemberStore()
      if (store.profile) {
        store.profile.avatar = permanentUrl
      }
    }

    // 2. 有输入昵称则更新
    if (chosenNickname.value.trim()) {
      await putMemberProfileAPI({
        nickname: chosenNickname.value.trim(),
      })
      const store = useMemberStore()
      if (store.profile) {
        store.profile.nickname = chosenNickname.value.trim()
      }
    }

    uni.hideLoading()
    uni.showToast({ icon: 'success', title: '设置完成' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/my/my' })
    }, 500)
  } catch (e) {
    uni.hideLoading()
    // 即使更新失败也允许进入（默认头像/昵称仍可用）
    uni.showToast({ icon: 'none', title: '资料保存失败，可稍后在个人中心修改' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/my/my' })
    }, 1000)
  }
}

/** 跳过资料完善，直接进入 */
const onSkipProfile = () => {
  uni.switchTab({ url: '/pages/my/my' })
}
// #endif

// ==================== 密码登录（全平台） ====================

/** 是否展示密码登录表单 */
const showPwdForm = ref(false)

/** 密码登录表单 */
const form = ref({
  phone: '',
  password: '',
})

/** 密码登录提交 */
const onPasswordLogin = async () => {
  // 确保 phone 是字符串（微信 type="number" 可能返回 Number）
  const phone = String(form.value.phone || '').trim()
  if (!phone) {
    uni.showToast({ icon: 'none', title: '请输入手机号' })
    return
  }
  if (!/^1\d{10}$/.test(phone)) {
    uni.showToast({ icon: 'none', title: '请输入正确的手机号' })
    return
  }
  if (!form.value.password) {
    uni.showToast({ icon: 'none', title: '请输入密码' })
    return
  }

  uni.showLoading({ title: '登录中...', mask: true })

  try {
    const res = await postLoginAPI({
      phone,
      password: form.value.password,
    })
    console.log('[login] 密码登录成功, res=', JSON.stringify(res.data))
    loginSuccess(res.data)
  } catch (e) {
    console.error('[login] 密码登录失败', e)
  } finally {
    uni.hideLoading()
  }
}

// ==================== 登录成功处理 ====================

const loginSuccess = (profile: LoginResult) => {
  if (!profile || !profile.accessToken) {
    console.error('[login] loginSuccess 收到无效数据:', JSON.stringify(profile))
    uni.showToast({ icon: 'none', title: '登录失败，请重试' })
    return
  }
  console.log(
    '[login] loginSuccess 触发, accessToken=',
    profile.accessToken.substring(0, 20) + '...',
  )
  // 保存会员信息到 Pinia store（自动持久化）
  const memberStore = useMemberStore()
  memberStore.setProfile(profile)
  console.log(
    '[login] store.profile 已设置, accessToken=',
    memberStore.profile?.accessToken?.substring(0, 20) + '...',
  )

  uni.showToast({ icon: 'success', title: '登录成功' })
  setTimeout(() => {
    // switchTab 回到「我的」页面
    uni.switchTab({ url: '/pages/my/my' })
  }, 500)
}
</script>

<template>
  <view class="viewport">
    <!-- Logo -->
    <view class="logo">
      <image
        src="https://pcapi-xiaotuxian-front-devtest.itheima.net/miniapp/images/logo_icon.png"
        mode="aspectFit"
      />
    </view>

    <view class="login">
      <!-- 微信一键登录（仅小程序） -->
      <!-- #ifdef MP-WEIXIN -->
      <button
        v-if="!showBindPhone && !showProfileCompletion"
        class="button wechat-btn"
        @tap="onWechatLogin"
      >
        <text class="icon-weixin"></text>
        微信一键登录
      </button>
      <!-- #endif -->

      <!-- 手机号绑定（微信新用户） -->
      <!-- #ifdef MP-WEIXIN -->
      <view v-if="showBindPhone" class="bind-phone">
        <view class="bind-title">请绑定手机号完成注册</view>
        <view class="bind-desc">手机号将用于账号登录和订单通知</view>
        <input
          v-model="bindPhone"
          class="input"
          type="text"
          placeholder="请输入手机号"
          maxlength="11"
        />
        <button class="button bind-btn" @tap="onBindPhone">确认绑定</button>
        <view class="back-link" @tap="onCancelBind">← 返回</view>
      </view>

      <!-- 资料完善（微信新用户绑定手机号后） -->
      <view v-if="showProfileCompletion" class="profile-completion">
        <view class="completion-title">完善个人资料</view>
        <view class="completion-desc">设置头像和昵称，让大家认识你</view>

        <!-- 头像选择 -->
        <view class="avatar-section">
          <button class="avatar-wrapper" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
            <image
              class="avatar-preview"
              :src="
                chosenAvatarUrl ||
                'https://yjy-xiaotuxian-dev.oss-cn-beijing.aliyuncs.com/picture/2021-04-06/db628d42-88a7-46e7-abb8-659448c33081.png'
              "
              mode="aspectFill"
            />
            <view class="avatar-tip">点击选择头像</view>
          </button>
        </view>

        <!-- 昵称输入 -->
        <view class="nickname-section">
          <input
            v-model="chosenNickname"
            class="input nickname-input"
            type="nickname"
            placeholder="请输入昵称（微信昵称快捷键可见于键盘上方）"
            maxlength="20"
          />
        </view>

        <button class="button submit-btn" @tap="onSubmitProfile">确认设置</button>
        <view class="skip-link" @tap="onSkipProfile">暂不设置，直接进入 →</view>
      </view>
      <!-- #endif -->

      <!-- 其他登录方式分割线 -->
      <view class="extra" v-if="!showPwdForm && !showBindPhone && !showProfileCompletion">
        <view class="caption">
          <text>其他登录方式</text>
        </view>
        <view class="options">
          <button class="option-btn" @tap="showPwdForm = true">
            <text class="icon icon-user">账号密码登录</text>
          </button>
        </view>
      </view>

      <!-- 账号密码登录表单 -->
      <view v-if="showPwdForm" class="pwd-form">
        <input
          v-model="form.phone"
          class="input"
          type="text"
          placeholder="请输入手机号"
          maxlength="11"
        />
        <input
          v-model="form.password"
          class="input"
          type="text"
          password
          placeholder="请输入密码"
          maxlength="100"
        />
        <button class="button phone-btn" @tap="onPasswordLogin">登录</button>
        <view class="back-link" @tap="showPwdForm = false"> ← 返回其他登录方式 </view>
      </view>
    </view>

    <!-- 底部协议提示 -->
    <view class="tips"> 登录/注册即视为你同意《服务条款》和《小兔鲜儿隐私协议》 </view>

    <!-- 微信登录 - 绑定手机号 Sheet（仅小程序） -->
    <!-- #ifdef MP-WEIXIN -->
    <view v-if="showAuthSheet" class="auth-mask" @tap="onCloseAuthSheet"></view>
    <view v-if="showAuthSheet" class="auth-sheet">
      <view class="sheet-handle"></view>

      <!-- 顶部 header：应用 logo + 应用名 + 提示 -->
      <view class="sheet-header">
        <view class="header-left">
          <image class="header-logo" :src="appLogo" mode="aspectFit" />
          <text class="header-name">{{ defaultAppName }}</text>
        </view>
        <view class="header-info">
          <text class="info-icon">ⓘ</text>
        </view>
      </view>

      <!-- 标题 + 副标题 -->
      <view class="sheet-title">绑定手机号完成登录</view>
      <view class="sheet-desc">手机号将用于账号登录和订单通知</view>

      <!-- 手机号输入 -->
      <view class="phone-box">
        <input
          v-model="phoneInput"
          class="phone-input"
          type="number"
          placeholder="请输入手机号"
          maxlength="11"
        />
        <text class="phone-label">绑定后即可登录</text>
      </view>

      <!-- 允许 / 不允许 按钮 -->
      <button class="sheet-btn allow-btn" @tap="onAllowBindPhone">允许</button>
      <button class="sheet-btn deny-btn" @tap="onCloseAuthSheet">不允许</button>
    </view>
    <!-- #endif -->
  </view>
</template>

<style lang="scss">
page {
  height: 100%;
}

.viewport {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20rpx 40rpx;
  position: relative;
}

.logo {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  image {
    width: 220rpx;
    height: 220rpx;
  }
}

.login {
  display: flex;
  flex-direction: column;
  height: 60vh;
  padding: 40rpx 20rpx 20rpx;

  // 共用按钮基础样式
  .button {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 80rpx;
    font-size: 28rpx;
    border-radius: 72rpx;
    color: #fff;
  }

  // 微信登录按钮
  .wechat-btn {
    background-color: #06c05f;
    margin-bottom: 40rpx;

    .icon-weixin {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 44rpx;
      height: 44rpx;
      margin-right: 12rpx;
      font-size: 36rpx;
      &::before {
        content: '';
        display: block;
        width: 40rpx;
        height: 40rpx;
        background: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="white"><path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18zm3.702 3.435c-2.424 0-4.595 1.523-4.595 3.648 0 2.125 2.171 3.648 4.595 3.648.358 0 .692-.065 1.024-.134a.707.707 0 0 1 .59.086l1.561.914c.042.044.135.044.135 0 0-.048-.023-.11-.038-.157l-.32-1.215a.486.486 0 0 1 .175-.546c1.496-1.102 2.371-2.434 2.371-4.008 0-2.125-2.171-3.648-4.595-3.648zm-2.333 1.966c.526 0 .953.435.953.97 0 .536-.427.97-.953.97a.962.962 0 0 1-.954-.97c0-.535.428-.97.954-.97zm4.667 0c.526 0 .953.435.953.97 0 .536-.427.97-.953.97a.962.962 0 0 1-.954-.97c0-.535.428-.97.954-.97z"/></svg>')
          no-repeat center;
        background-size: contain;
      }
    }
  }

  // 密码登录按钮
  .phone-btn {
    background-color: #28bb9c;
  }

  // 手机号绑定区域
  .bind-phone {
    display: flex;
    flex-direction: column;

    .bind-title {
      font-size: 32rpx;
      font-weight: bold;
      color: #333;
      text-align: center;
      margin-bottom: 12rpx;
    }

    .bind-desc {
      font-size: 24rpx;
      color: #999;
      text-align: center;
      margin-bottom: 40rpx;
    }

    .input {
      width: 100%;
      height: 80rpx;
      font-size: 28rpx;
      border-radius: 72rpx;
      border: 1px solid #ddd;
      padding: 0 30rpx;
      margin-bottom: 20rpx;
      box-sizing: border-box;
      background-color: #fff;
      text-align: center;
    }

    .bind-btn {
      background-color: #28bb9c;
    }

    .back-link {
      text-align: center;
      font-size: 26rpx;
      color: #999;
      margin-top: 30rpx;
      padding: 10rpx;
    }
  }

  // 资料完善区域
  .profile-completion {
    display: flex;
    flex-direction: column;
    align-items: center;

    .completion-title {
      font-size: 34rpx;
      font-weight: bold;
      color: #333;
      text-align: center;
      margin-bottom: 12rpx;
    }

    .completion-desc {
      font-size: 24rpx;
      color: #999;
      text-align: center;
      margin-bottom: 50rpx;
    }

    .avatar-section {
      display: flex;
      justify-content: center;
      margin-bottom: 40rpx;

      .avatar-wrapper {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 0;
        margin: 0;
        background-color: transparent;
        border: none;
        line-height: 1;
        &::after {
          border: none;
        }
      }

      .avatar-preview {
        width: 160rpx;
        height: 160rpx;
        border-radius: 50%;
        background-color: #f0f0f0;
        border: 4rpx solid #e0e0e0;
      }

      .avatar-tip {
        margin-top: 16rpx;
        font-size: 24rpx;
        color: #27ba9b;
      }
    }

    .nickname-section {
      width: 100%;
      margin-bottom: 40rpx;

      .nickname-input {
        width: 100%;
        height: 80rpx;
        font-size: 28rpx;
        border-radius: 72rpx;
        border: 1px solid #ddd;
        padding: 0 30rpx;
        box-sizing: border-box;
        background-color: #fff;
        text-align: center;
      }
    }

    .submit-btn {
      background-color: #27ba9b;
      margin-bottom: 24rpx;
    }

    .skip-link {
      font-size: 26rpx;
      color: #999;
      padding: 10rpx;
    }
  }

  // 密码表单区域
  .pwd-form {
    display: flex;
    flex-direction: column;

    .input {
      width: 100%;
      height: 80rpx;
      font-size: 28rpx;
      border-radius: 72rpx;
      border: 1px solid #ddd;
      padding: 0 30rpx;
      margin-bottom: 20rpx;
      box-sizing: border-box;
      background-color: #fff;
    }

    .back-link {
      text-align: center;
      font-size: 26rpx;
      color: #999;
      margin-top: 30rpx;
      padding: 10rpx;
    }
  }

  // 其他登录方式
  .extra {
    flex: 1;
    padding: 70rpx 70rpx 0;

    .caption {
      width: 440rpx;
      line-height: 1;
      border-top: 1rpx solid #ddd;
      font-size: 26rpx;
      color: #999;
      position: relative;
      text {
        transform: translate(-40%);
        background-color: #fff;
        position: absolute;
        top: -12rpx;
        left: 50%;
      }
    }

    .options {
      display: flex;
      justify-content: center;
      align-items: center;
      margin-top: 70rpx;

      .option-btn {
        padding: 0;
        background-color: transparent;
        font-size: 28rpx;
        color: #333;
        &::after {
          border: none;
        }
      }
    }
  }

  // 账号登录图标
  .icon-user::before {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 80rpx;
    height: 80rpx;
    margin: 0 auto 6rpx;
    font-size: 40rpx;
    border: 1rpx solid #444;
    border-radius: 50%;
    content: '👤';
  }

  .icon {
    font-size: 24rpx;
    color: #444;
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: 8rpx;
  }
}

// 底部协议
.tips {
  position: absolute;
  bottom: 80rpx;
  left: 20rpx;
  right: 20rpx;
  font-size: 22rpx;
  color: #999;
  text-align: center;
}

// 微信登录 - 申请手机号 Sheet
.auth-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 998;
}

.auth-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 20rpx 40rpx 80rpx;
  z-index: 999;
  box-sizing: border-box;

  .sheet-handle {
    width: 60rpx;
    height: 8rpx;
    background-color: #e0e0e0;
    border-radius: 4rpx;
    margin: 0 auto 30rpx;
  }

  // 顶部 header：logo + 应用名 + i 提示
  .sheet-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 40rpx;

    .header-left {
      display: flex;
      align-items: center;
    }

    .header-logo {
      width: 56rpx;
      height: 56rpx;
      border-radius: 12rpx;
      margin-right: 16rpx;
    }

    .header-name {
      font-size: 30rpx;
      font-weight: 600;
      color: #333;
    }

    .header-info {
      width: 44rpx;
      height: 44rpx;
      display: flex;
      align-items: center;
      justify-content: center;

      .info-icon {
        font-size: 32rpx;
        color: #ccc;
      }
    }
  }

  // 标题 + 副标题
  .sheet-title {
    font-size: 36rpx;
    font-weight: bold;
    color: #1a1a1a;
    line-height: 1.4;
    margin-bottom: 16rpx;
  }

  .sheet-desc {
    font-size: 26rpx;
    color: #999;
    line-height: 1.5;
    margin-bottom: 50rpx;
  }

  // 中间号码方框
  .phone-box {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 180rpx;
    background-color: #f7f7f7;
    border-radius: 16rpx;
    margin-bottom: 50rpx;

    .phone-number {
      font-size: 44rpx;
      font-weight: 500;
      color: #1a1a1a;
      letter-spacing: 2rpx;
      margin-bottom: 12rpx;
    }

    .phone-label {
      font-size: 24rpx;
      color: #999;
    }

    .phone-input {
      width: 80%;
      height: 60rpx;
      font-size: 36rpx;
      color: #1a1a1a;
      text-align: center;
      background-color: transparent;
      margin-bottom: 12rpx;
    }
  }

  // 按钮基础样式
  .sheet-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 88rpx;
    font-size: 30rpx;
    font-weight: 500;
    border-radius: 16rpx;
    margin-bottom: 20rpx;
    line-height: 1;
  }

  .allow-btn {
    background-color: #07c160;
    color: #fff;
  }

  .deny-btn {
    background-color: #f5f5f5;
    color: #333;
  }

  // "使用其它号码" 链接
  .use-other {
    text-align: center;
    font-size: 28rpx;
    color: #576b95;
    padding: 20rpx 0;
  }
}
</style>
