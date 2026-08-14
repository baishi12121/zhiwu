/**
 * 智能客服 API
 *
 * 对接 mall-ai-service 的 /ai/chat/stream SSE 兼容网关接口
 * 由于微信小程序不支持 EventSource，使用 uni.request + enableChunked 接收流式响应
 */

import { createSseMessageParser } from './ai-parser'
import { useMemberStore } from '@/stores'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

/** SSE 消息类型 */
export type ChatMessageType =
  | 'token'
  | 'thinking'
  | 'tool_call'
  | 'tool_result'
  | 'citation'
  | 'finish'
  | 'error'

/** SSE 消息结构
 *
 * <p>对接 Spring Boot SSE 网关输出。
 * 消息类型：
 *   - token: AI 自然语言回复 token
 *   - thinking: Agent 思考进度
 *   - tool_result: 结构化查询数据
 *   - finish: 正常结束
 *   - error: 异常消息
 */
export interface ChatMessage {
  type: ChatMessageType
  /** 统一内容字段 */
  content?: string
  /** 兼容旧错误消息字段 */
  message?: string
  /** 兼容结构化数据字段 */
  data?: any
}

/** 聊天请求参数 */
export interface ChatParams {
  /** 用户提问内容 */
  query: string
  /** 收到消息的回调 */
  onMessage?: (msg: ChatMessage) => void
  /** 流结束的回调 */
  onDone?: () => void
  /** 错误回调 */
  onError?: (err: string) => void
}

type ChunkedRequestTask = UniApp.RequestTask & {
  onChunkReceived?: (callback: (res: { data: ArrayBuffer }) => void) => void
}

/**
 * 发送智能客服提问（SSE 流式接收）
 *
 * <p>使用 uni.request 的 enableChunked 选项接收分块响应，
 * 手动解析 SSE 格式（data: xxx\n\n）。
 *
 * @param params 聊天参数
 * @returns 请求任务（可用于中断请求）
 */
export const postChatAPI = (params: ChatParams): UniApp.RequestTask => {
  let receivedChunk = false
  let completedWithError = false
  const parser = createSseMessageParser<ChatMessage>((msg) => {
    params.onMessage?.(msg)
  })
  const query = encodeURIComponent(params.query)
  const conversationId = encodeURIComponent(`miniapp-${Date.now()}`)
  const memberStore = useMemberStore()
  const userId = memberStore.profile?.userId
  const token = memberStore.profile?.accessToken
  const userQuery = userId ? `&userId=${encodeURIComponent(String(userId))}` : ''

  const task = uni.request({
    url: `${baseURL}/ai/chat/stream?question=${query}&conversationId=${conversationId}${userQuery}`,
    method: 'GET',
    header: {
      'source-client': 'miniapp',
      ...(userId ? { 'X-User-Id': String(userId) } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      Accept: 'text/event-stream',
    },
    dataType: 'text',
    // 启用分块接收（微信小程序基础库 2.20.2+）
    enableChunked: true,
    // 超时需对齐后端 spring.mvc.async.request-timeout=300s，
    // Python LangGraph 工作流通常需要 60s+，前端 60s 会先超时
    timeout: 300000,
    // 响应完成；非分片环境下 res.data 可能一次性携带完整 SSE 文本
    success(res) {
      if (res.statusCode < 200 || res.statusCode >= 300) {
        completedWithError = true
        params.onError?.(getHttpErrorMessage(res.statusCode, res.data))
        return
      }
      if (!receivedChunk) {
        if (typeof res.data === 'string') {
          parser.push(res.data)
        } else if (res.data instanceof ArrayBuffer) {
          parser.push(arrayBufferToString(res.data))
        }
      }
      parser.flush()
      if (!completedWithError) {
        params.onDone?.()
      }
    },
    fail(err) {
      completedWithError = true
      params.onError?.(err.errMsg || '网络错误')
    },
  }) as ChunkedRequestTask

  task.onChunkReceived?.((res) => {
    try {
      receivedChunk = true
      parser.push(arrayBufferToString(res.data))
    } catch (e) {
      console.error('解析 SSE 消息失败', e)
    }
  })

  return task
}

// SSE 流式 UTF-8 解码器（全局单例）
// TextDecoder 的 stream:true 模式会内部缓存跨 chunk 的 incomplete 多字节序列，
// 避免中文等 UTF-8 多字节字符被 TCP 分片切断后产出的 U+FFFD（�）乱码
let sseDecoder: TextDecoder | null = null

/**
 * ArrayBuffer 转字符串（UTF-8，流式安全）
 *
 * 微信小程序环境 SSE 分块接收时，UTF-8 编码的中文（3 字节）可能被
 * 切分到两个 ArrayBuffer chunk 中。单次 new TextDecoder().decode()
 * 不启用 stream 模式，遇到不完整字节序列会产出 U+FFFD 乱码。
 *
 * 使用全局 TextDecoder + { stream: true } 可让解码器内部保留
 * 未完成的字节序列，下次 decode 时自动拼接。
 */
function arrayBufferToString(buffer: ArrayBuffer): string {
  if (typeof TextDecoder === 'undefined') {
    // 微信小程序旧版基础库无 TextDecoder，手动 UTF-8 解码
    const bytes = new Uint8Array(buffer)
    let result = ''
    for (let i = 0; i < bytes.length; i++) {
      result += String.fromCharCode(bytes[i])
    }
    return decodeURIComponent(escape(result))
  }
  if (!sseDecoder) sseDecoder = new TextDecoder('utf-8')
  // stream:true — 关键！内部缓存跨调用未完成的 UTF-8 序列
  return sseDecoder.decode(new Uint8Array(buffer), { stream: true })
}

function getHttpErrorMessage(statusCode: number, data: unknown): string {
  const fallback = `请求失败，HTTP ${statusCode}`
  if (typeof data !== 'string' || !data.trim()) return fallback
  try {
    const body = JSON.parse(data)
    return body.message || body.error || fallback
  } catch {
    return data.length > 80 ? fallback : data
  }
}
