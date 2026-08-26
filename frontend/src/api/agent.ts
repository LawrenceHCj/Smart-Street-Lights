import request from './request'

export interface Source {
  title: string
  section: string
  score: number
}

export interface AskResponse {
  answer: string
  sources: Source[]
  /** 后端自动创建会话时返回的会话 id；已有会话时原样返回 */
  conversationId?: string
}

export interface Conversation {
  conversationId: string
  title: string
  createdAt: string
  updatedAt: string
  lastMessageAt: string
}

export interface AgentMessage {
  id: number
  messageId: string
  conversationId: string
  role: 'user' | 'assistant'
  content: string
  /** assistant 消息存 sources 的 JSON 数组；解析失败时忽略 */
  metadata: string | null
  createdAt: string
}

/** 智能问答：POST /api/agent/ask，conversationId 为空时后端自动新建会话 */
export function ask(question: string, conversationId?: string): Promise<AskResponse> {
  return request.post('/agent/ask', { question, conversationId })
}

/** 会话列表：GET /api/assistant/conversations（按最近更新倒序） */
export function listConversations(): Promise<Conversation[]> {
  return request.get('/assistant/conversations', { silent: true })
}

/** 新建会话：POST /api/assistant/conversations（可带首条问题作标题） */
export function createConversation(question?: string): Promise<Conversation> {
  return request.post('/assistant/conversations', { question }, { silent: true })
}

/** 读取会话历史消息：GET /api/assistant/conversations/{id}/messages */
export function getConversationMessages(conversationId: string): Promise<AgentMessage[]> {
  return request.get(`/assistant/conversations/${conversationId}/messages`, { silent: true })
}

/** 删除会话：DELETE /api/assistant/conversations/{id} */
export function deleteConversation(conversationId: string): Promise<null> {
  return request.delete(`/assistant/conversations/${conversationId}`, { silent: true })
}

/** 防御性解析 assistant 消息的 metadata（sources JSON 数组），非法/非数组返回空 */
export function parseMessageSources(metadata: string | null): Source[] {
  if (!metadata) return []
  try {
    const parsed = JSON.parse(metadata)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (s): s is Source =>
        !!s && typeof s.title === 'string' && typeof s.section === 'string' && typeof s.score === 'number',
    )
  } catch {
    return []
  }
}
