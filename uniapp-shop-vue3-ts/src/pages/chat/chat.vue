<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { postChatAPI, type ChatMessage } from '@/services/ai'
import type { ChatRecord } from '@/types/ai'
import type { GoodsItem } from '@/types/global'

// 聊天消息列表
const messages = ref<ChatRecord[]>([
  {
    id: 'welcome',
    role: 'assistant',
    content:
      '您好！我是智能客服助手，可以帮您查询商品价格、库存、优惠活动等信息。请问有什么可以帮您？',
    status: 'done',
    createTime: Date.now(),
  },
])

const inputText = ref('')
const loading = ref(false)
let currentTask: UniApp.RequestTask | null = null
let typewriterTimer: ReturnType<typeof setInterval> | null = null

const presetQuestions = [
  '春款法式连衣裙多少钱',
  '无线降噪蓝牙耳机还有货吗',
  '现在有什么优惠券',
  'T恤多少钱',
  '连衣裙有哪些尺寸',
]

const stopTypewriter = () => {
  if (typewriterTimer) {
    clearInterval(typewriterTimer)
    typewriterTimer = null
  }
}

/**
 * 打字机逐字输出，结束后自动移除闪烁光标
 */
const typewriteText = (target: ChatRecord, fullText: string, speed = 30) => {
  stopTypewriter()
  target.typing = true
  let index = 0
  target.content = ''
  typewriterTimer = setInterval(() => {
    if (index < fullText.length) {
      target.content += fullText[index]
      index++
    } else {
      stopTypewriter()
      target.typing = false
    }
  }, speed)
}

/**
 * 尝试将后端返回的数据解析为商品列表
 */
const parseGoods = (data: any): GoodsItem[] | null => {
  if (!Array.isArray(data) || data.length === 0) return null
  const goods = data
    .map((row: any): GoodsItem | null => {
      if (!row || typeof row !== 'object') return null
      const id = String(row.id ?? row.goods_id ?? row.product_id ?? row.spu_id ?? row._id ?? '')
      const name =
        row.name ??
        row.title ??
        row.goods_name ??
        row.product_name ??
        row.pname ??
        row['商品名称'] ??
        row['商品名'] ??
        ''
      const picture =
        row.picture ??
        row.image ??
        row.img ??
        row.picture_url ??
        row.mainPicture ??
        row.main_picture ??
        row.pic ??
        row.url ??
        row['图片'] ??
        ''
      const price = Number(
        row.price ??
          row.current_price ??
          row.sale_price ??
          row.salePrice ??
          row.amount ??
          row['价格'] ??
          0,
      )
      if (!id || !name || (!picture && !(price > 0))) return null
      return {
        id,
        name,
        desc: row.desc ?? row.description ?? row.goods_desc ?? '',
        picture,
        price,
        discount: Number(row.discount ?? 0),
        orderNum: Number(row.orderNum ?? row.order_num ?? 0),
      }
    })
    .filter((g): g is GoodsItem => g !== null)
  return goods.length > 0 ? goods : null
}

const handleSend = async () => {
  const query = inputText.value.trim()
  if (!query || loading.value) return

  stopTypewriter()
  if (currentTask) {
    currentTask.abort()
    currentTask = null
  }

  const userMsg: ChatRecord = {
    id: `user-${Date.now()}`,
    role: 'user',
    content: query,
    status: 'done',
    createTime: Date.now(),
  }
  messages.value.push(userMsg)
  inputText.value = ''

  const assistantMsg: ChatRecord = {
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    content: '',
    status: 'thinking',
    createTime: Date.now(),
  }
  messages.value.push(assistantMsg)

  loading.value = true
  await scrollToBottom()

  let hasReply = false
  let streamedReply = ''
  let failed = false

  currentTask = postChatAPI({
    query,
    onMessage: (msg: ChatMessage) => {
      const target = messages.value.find((m) => m.id === assistantMsg.id)
      if (!target) return

      if (msg.type === 'thinking') {
        // 后端流水线进度 → 静默忽略，前端只显示"正在思考中..."
        return
      }

      if (msg.type === 'token') {
        // AI 自然语言回复 token → 直接追加，保持真正流式体验
        hasReply = true
        target.status = 'done'
        const token = msg.content || ''
        if (token && !/�/.test(token)) {
          stopTypewriter()
          target.typing = true
          streamedReply += token
          target.content = streamedReply
        }
      } else if (msg.type === 'tool_result') {
        // 结构化数据 → 商品卡片（不干扰打字机）
        const data = parseToolResult(msg.content)
        const goods = parseGoods(data)
        if (goods) target.goods = goods
        if (!hasReply) {
          target.status = 'done'
          typewriteText(target, formatResult({ ...msg, data }))
        }
      } else if (msg.type === 'finish') {
        target.typing = false
        target.status = 'done'
      } else if (msg.type === 'error') {
        failed = true
        stopTypewriter()
        target.typing = false
        target.status = 'error'
        target.content = msg.content || msg.message || '查询失败，请稍后重试'
      }
      scrollToBottom()
    },
    onDone: () => {
      if (failed) {
        loading.value = false
        currentTask = null
        return
      }
      loading.value = false
      currentTask = null
      const target = messages.value.find((m) => m.id === assistantMsg.id)
      if (target && target.status === 'thinking') {
        target.status = 'error'
        target.content = '服务未返回有效结果，请稍后重试'
      }
    },
    onError: (err: string) => {
      failed = true
      loading.value = false
      currentTask = null
      stopTypewriter()
      const target = messages.value.find((m) => m.id === assistantMsg.id)
      if (target) {
        target.typing = false
        target.status = 'error'
        target.content = `服务暂时不可用：${err}`
      }
    },
  })
}

const formatResult = (msg: ChatMessage): string => {
  if (!msg.data) return msg.message || '查询完成'
  if (typeof msg.data === 'string') return msg.data
  if (Array.isArray(msg.data)) {
    return msg.data
      .map((row: any) => {
        if (typeof row === 'object') {
          const parts = Object.entries(row)
            .filter(([, v]) => v !== null && v !== undefined)
            .map(([k, v]) => `${k}：${v}`)
          return parts.length > 0 ? parts.join('，') : JSON.stringify(row)
        }
        return String(row)
      })
      .join('\n')
  }
  if (typeof msg.data === 'object') {
    return Object.entries(msg.data)
      .filter(([, v]) => v !== null && v !== undefined)
      .map(([k, v]) => `${k}：${v}`)
      .join('\n')
  }
  return String(msg.data)
}

const parseToolResult = (content?: string): any => {
  if (!content) return null
  try {
    return JSON.parse(content)
  } catch {
    return content
  }
}

const handlePresetQuestion = (question: string) => {
  inputText.value = question
  handleSend()
}

const scrollToBottom = async () => {
  await nextTick()
  const query = uni.createSelectorQuery()
  query.select('.chat-list').boundingClientRect()
  query.selectViewport().scrollOffset()
  query.exec((res) => {
    if (res[0] && res[1]) {
      uni.pageScrollTo({
        scrollTop: res[0].height + res[1].scrollTop,
        duration: 100,
      })
    }
  })
}

const handleClear = () => {
  uni.showModal({
    title: '提示',
    content: '确定清空所有聊天记录吗？',
    success: (res) => {
      if (res.confirm) {
        stopTypewriter()
        if (currentTask) {
          currentTask.abort()
          currentTask = null
        }
        loading.value = false
        messages.value = [
          {
            id: 'welcome',
            role: 'assistant',
            content: '聊天已清空。请问有什么可以帮您？',
            status: 'done',
            createTime: Date.now(),
          },
        ]
      }
    },
  })
}
</script>

<template>
  <view class="chat-page">
    <!-- 顶部操作栏 -->
    <view class="header">
      <text class="title">智能客服</text>
      <text class="clear-btn" @tap="handleClear">清空</text>
    </view>

    <!-- 聊天列表 -->
    <scroll-view class="chat-list" scroll-y :scroll-top="0" :scroll-with-animation="true">
      <view
        v-for="msg in messages"
        :key="msg.id"
        class="msg-item"
        :class="msg.role === 'user' ? 'msg-user' : 'msg-assistant'"
      >
        <!-- 头像 -->
        <view class="avatar" :class="msg.role === 'user' ? 'avatar-user' : 'avatar-bot'">
          <text v-if="msg.role === 'user'" class="avatar-text">我</text>
          <text v-else class="avatar-text">AI</text>
        </view>

        <!-- 消息内容 -->
        <view
          class="bubble"
          :class="[
            msg.role === 'user' ? 'bubble-user' : 'bubble-bot',
            msg.goods && msg.goods.length ? 'has-goods' : '',
          ]"
        >
          <text v-if="msg.status === 'thinking'" class="thinking">
            <text class="dot-pulse">●</text> 正在思考中...
          </text>
          <text v-else class="content"
            >{{ msg.content }}<text v-if="msg.typing" class="cursor-blink">|</text></text
          >
          <!-- 商品卡片列表（仅助手消息且有结构化商品数据时展示） -->
          <view v-if="msg.goods && msg.goods.length" class="goods-list">
            <navigator
              v-for="goods in msg.goods"
              :key="goods.id"
              class="goods-card"
              :url="`/pages/goods/goods?id=${goods.id}`"
              hover-class="none"
            >
              <image class="goods-pic" :src="goods.picture" mode="aspectFill" lazy-load />
              <view class="goods-info">
                <text class="goods-name">{{ goods.name }}</text>
                <text v-if="goods.desc" class="goods-desc">{{ goods.desc }}</text>
                <text class="goods-price">¥{{ goods.price }}</text>
              </view>
            </navigator>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- 预设问题 -->
    <view v-if="messages.length <= 1" class="preset-section">
      <view class="preset-title">猜你想问：</view>
      <view class="preset-list">
        <view
          v-for="q in presetQuestions"
          :key="q"
          class="preset-item"
          @tap="handlePresetQuestion(q)"
        >
          <text>{{ q }}</text>
        </view>
      </view>
    </view>

    <!-- 输入区域 -->
    <view class="input-section">
      <input
        v-model="inputText"
        class="input"
        type="text"
        placeholder="请输入您的问题..."
        confirm-type="send"
        :disabled="loading"
        @confirm="handleSend"
      />
      <button class="send-btn" :disabled="!inputText.trim() || loading" @tap="handleSend">
        {{ loading ? '查询中' : '发送' }}
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f5f5;
}

/* 顶部操作栏 */
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 32rpx;
  background-color: #fff;
  border-bottom: 1rpx solid #eee;

  .title {
    font-size: 32rpx;
    font-weight: bold;
    color: #333;
  }

  .clear-btn {
    font-size: 26rpx;
    color: #999;
  }
}

/* 聊天列表 */
.chat-list {
  flex: 1;
  padding: 24rpx;
  overflow-y: auto;
}

.msg-item {
  display: flex;
  margin-bottom: 32rpx;

  &.msg-user {
    flex-direction: row-reverse;
  }
}

.avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &.avatar-user {
    background-color: #27ba9b;
  }

  &.avatar-bot {
    background-color: #5c8dff;
  }

  .avatar-text {
    color: #fff;
    font-size: 24rpx;
    font-weight: bold;
  }
}

.bubble {
  max-width: 70%;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;
  margin: 0 16rpx;
  word-break: break-all;

  &.bubble-user {
    background-color: #27ba9b;
    color: #fff;
    border-top-right-radius: 4rpx;
  }

  &.bubble-bot {
    background-color: #fff;
    color: #333;
    border-top-left-radius: 4rpx;
    box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.05);
    /* 有商品卡片时允许更宽，便于展示商品信息 */
    &.has-goods {
      max-width: 88%;
    }
  }

  .thinking {
    color: #999;
    font-style: italic;
  }

  /* 思考中的呼吸动画 */
  .dot-pulse {
    animation: dot-breathe 1.4s ease-in-out infinite;
    display: inline-block;
  }

  /* 流式输出中的闪烁光标 */
  .cursor-blink {
    animation: cursor-flicker 0.8s step-end infinite;
    color: #27ba9b;
    font-weight: bold;
  }

  .content {
    white-space: pre-wrap;
    line-height: 1.6;
  }
}

/* 商品卡片列表 */
.goods-list {
  margin-top: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.goods-card {
  display: flex;
  padding: 16rpx;
  background-color: #fafafa;
  border-radius: 12rpx;
  border: 1rpx solid #eee;
}

.goods-pic {
  width: 120rpx;
  height: 120rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
  background-color: #f5f5f5;
}

.goods-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  margin-left: 16rpx;
  overflow: hidden;
}

.goods-name {
  font-size: 26rpx;
  color: #333;
  font-weight: 500;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.goods-desc {
  font-size: 22rpx;
  color: #999;
  margin-top: 4rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.goods-price {
  font-size: 30rpx;
  color: #ff4444;
  font-weight: bold;
  margin-top: 8rpx;
}

/* 预设问题 */
.preset-section {
  padding: 24rpx 32rpx;
  background-color: #fff;

  .preset-title {
    font-size: 26rpx;
    color: #999;
    margin-bottom: 16rpx;
  }

  .preset-list {
    display: flex;
    flex-wrap: wrap;
    gap: 16rpx;
  }

  .preset-item {
    padding: 12rpx 24rpx;
    background-color: #f0f9ff;
    border: 1rpx solid #d0e8ff;
    border-radius: 24rpx;
    font-size: 26rpx;
    color: #27ba9b;
  }
}

/* 输入区域 */
.input-section {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  background-color: #fff;
  border-top: 1rpx solid #eee;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));

  .input {
    flex: 1;
    height: 72rpx;
    padding: 0 24rpx;
    background-color: #f5f5f5;
    border-radius: 36rpx;
    font-size: 28rpx;
  }

  .send-btn {
    margin-left: 16rpx;
    padding: 0 32rpx;
    height: 72rpx;
    line-height: 72rpx;
    background-color: #27ba9b;
    color: #fff;
    border-radius: 36rpx;
    font-size: 28rpx;

    &[disabled] {
      background-color: #ccc;
    }
  }
}

/* ── 动画关键帧 ── */

/* 思考中点：呼吸灯效果 */
@keyframes dot-breathe {
  0%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1.2);
  }
}

/* 打字机光标：闪烁 */
@keyframes cursor-flicker {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}
</style>
