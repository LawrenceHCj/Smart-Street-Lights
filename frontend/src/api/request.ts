import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearSession } from '../utils/auth'

// 自定义请求配置项：silent = 该请求失败时不弹全局错误提示。
// 用于"约定接口"（后端可能尚未实现），由调用方自行降级处理。
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

/** 业务错误（body.code != 0）：保留 code / data，便于调用方按 401/403/400/500 区分处理。 */
export class ApiBusinessError extends Error {
  constructor(
    public code: number,
    message: string,
    public data?: unknown,
  ) {
    super(message)
    this.name = 'ApiBusinessError'
  }
}

const request = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

let redirectingToLogin = false

function handleExpiredSession(requestUrl?: string) {
  if (requestUrl === '/auth/login' || redirectingToLogin) return
  redirectingToLogin = true
  clearSession()
  ElMessage.warning('登录已失效，请重新登录')
  window.location.replace('/login')
}

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (resp) => {
    const r = resp.data
    if (r.code !== 0) {
      if (r.code === 401) handleExpiredSession(resp.config.url)
      else if (!resp.config.silent) ElMessage.error(r.message || '请求失败')
      return Promise.reject(new ApiBusinessError(r.code, r.message || '请求失败', r.data))
    }
    return r.data
  },
  (err) => {
    if (!err?.config?.silent) ElMessage.error(err.message || '网络错误')
    return Promise.reject(err)
  },
)

export default request
