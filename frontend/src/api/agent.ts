import request from './request'

export interface Source {
  title: string
  section: string
  score: number
}

export interface AskResponse {
  answer: string
  sources: Source[]
}

export function ask(question: string): Promise<AskResponse> {
  return request.post('/agent/ask', { question })
}
