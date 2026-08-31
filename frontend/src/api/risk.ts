import request from './request'
import type { AxiosRequestConfig } from 'axios'

/**
 * 预测性维护 API：设备未来 7 天故障风险（滑动窗口特征 + 加权风险模型）
 */

export interface RiskFeature {
  key: string
  label: string
  value: number | null
  valueUnit: string | null
  risk: number
  weight: number
  riskContribution: number
  detail: string
  sampleCount: number
  insufficient: boolean
}

export type RiskLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export interface RiskPrediction {
  id: number
  deviceCode: string
  predictedAt: string
  riskLevel: RiskLevel
  riskScore: number
  horizonDays: number
  currentHealthScore: number | null
  /** 后端存储为 JSON 字符串，用 parseFeatures/parseReasons 解析 */
  features: string
  reasons: string
  advice: string
  createdAt: string
}

export function parseFeatures(p: RiskPrediction): RiskFeature[] {
  try {
    const parsed = JSON.parse(p.features)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function parseReasons(p: RiskPrediction): string[] {
  try {
    const parsed = JSON.parse(p.reasons)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function listRiskLatest(config?: AxiosRequestConfig): Promise<RiskPrediction[]> {
  return request.get('/risk/latest', config)
}

export function getRiskHistory(deviceCode: string, config?: AxiosRequestConfig): Promise<RiskPrediction[]> {
  return request.get(`/risk/history/${encodeURIComponent(deviceCode)}`, config)
}

export function predictDevice(deviceCode: string, config?: AxiosRequestConfig): Promise<RiskPrediction> {
  return request.post(`/risk/predict/${encodeURIComponent(deviceCode)}`, undefined, config)
}

export function predictAllDevices(config?: AxiosRequestConfig): Promise<{ predictedDevices: number }> {
  return request.post('/risk/predict-all', undefined, config)
}

export function seedDemoData(config?: AxiosRequestConfig): Promise<string> {
  return request.post('/risk/seed-demo', undefined, config)
}
