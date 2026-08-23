import axios from 'axios'
import { ElMessage } from 'element-plus'

// 自定义请求配置项：silent = 该请求失败时不弹全局错误提示。
// 用于"约定接口"（后端可能尚未实现），由调用方自行降级处理。
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

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
      if (!resp.config.silent) ElMessage.error(r.message || '请求失败')
      return Promise.reject(new Error(r.message || '请求失败'))
    }
    return r.data
  },
  (err) => {
    if (!err?.config?.silent) ElMessage.error(err.message || '网络错误')
    return Promise.reject(err)
  },
)

export default request
