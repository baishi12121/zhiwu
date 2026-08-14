import type { ChatMessage } from './ai'

type MessageHandler<T> = (msg: T) => void

const SSE_FRAME_SEPARATOR = /\r?\n\r?\n/

export const createSseMessageParser = <T extends ChatMessage = ChatMessage>(
  onMessage: MessageHandler<T>,
) => {
  let buffer = ''

  const emitJson = (jsonStr: string) => {
    const payload = jsonStr.trim()
    if (!payload || payload === '[DONE]') return
    try {
      onMessage(JSON.parse(payload) as T)
    } catch {
      onMessage({ type: 'progress', message: payload } as T)
    }
  }

  const emitFrame = (frame: string) => {
    const dataLines = frame
      .replace(/\r\n/g, '\n')
      .split('\n')
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.substring(5).trimStart())

    if (dataLines.length > 0) {
      emitJson(dataLines.join('\n'))
      return
    }

    const trimmed = frame.trim()
    if (trimmed.startsWith('{')) emitJson(trimmed)
  }

  const parseBareJsonIfComplete = () => {
    const trimmed = buffer.trim()
    if (!trimmed.startsWith('{')) return
    try {
      onMessage(JSON.parse(trimmed) as T)
      buffer = ''
    } catch {
      // Keep incomplete JSON in the buffer until a later chunk completes it.
    }
  }

  const drainFrames = () => {
    let separatorMatch = buffer.match(SSE_FRAME_SEPARATOR)
    while (separatorMatch?.index !== undefined) {
      const frame = buffer.slice(0, separatorMatch.index)
      buffer = buffer.slice(separatorMatch.index + separatorMatch[0].length)
      emitFrame(frame)
      separatorMatch = buffer.match(SSE_FRAME_SEPARATOR)
    }
    parseBareJsonIfComplete()
  }

  return {
    push(chunk: string) {
      buffer += chunk
      drainFrames()
    },
    flush() {
      if (!buffer.trim()) return
      emitFrame(buffer)
      buffer = ''
    },
  }
}
