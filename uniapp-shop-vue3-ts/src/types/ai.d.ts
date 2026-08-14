/**
 * 智能客服相关类型定义
 */

import type { GoodsItem } from './global'

/** 聊天消息角色 */
export type ChatRole = 'user' | 'assistant'

/** 聊天消息状态 */
export type ChatStatus = 'thinking' | 'streaming' | 'done' | 'error'

/** 聊天消息 */
export interface ChatRecord {
  /** 唯一 ID */
  id: string
  /** 消息角色 */
  role: ChatRole
  /** 消息内容（纯文本摘要，详细数据走 goods） */
  content: string
  /** 消息状态 */
  status: ChatStatus
  /** 创建时间戳 */
  createTime: number
  /** 结构化商品列表（仅当后端返回商品查询结果时存在） */
  goods?: GoodsItem[]
  /** 是否正在打字机逐字输出（用于显示闪烁光标） */
  typing?: boolean
}
