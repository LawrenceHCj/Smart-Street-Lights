import request from './request'

export interface LoginResponse {
  token: string
  username: string
  role: string
}

export function login(username: string, password: string): Promise<LoginResponse> {
  return request.post('/auth/login', { username, password })
}
