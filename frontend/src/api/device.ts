import request from './request'
import type { AxiosRequestConfig } from 'axios'

export interface DeviceVO {
  id: number
  code: string
  location: string
  longitude: number | null
  latitude: number | null
  status: 'ONLINE' | 'OFFLINE'
  latestLux: number | null
  lastSeen: number | null
}

export interface LightPoint {
  ts: number
  lux: number
}

export interface HistoryVO {
  deviceId: string
  points: LightPoint[]
}

export interface DashboardOverview {
  totalDevices: number
  onlineCount: number
  offlineCount: number
  avgLux: number
}

export interface CurrentLight {
  deviceId: string
  lux: number | null
  ts: number | null
}

export function listDevices(config?: AxiosRequestConfig): Promise<DeviceVO[]> {
  return request.get('/devices', config)
}

export interface HealthAnomaly {
  issue: string
  reason: string
  deduct: number
}

export interface DeviceHealthReport {
  id: number | null
  deviceCode: string
  healthScore: number
  telemetry: HealthTelemetry | null
  anomalies: HealthAnomaly[]
  createdAt: string
}

export interface HealthTelemetry {
  lux: number | null
  temperature: number | null
  voltage: number | null
  current: number | null
  power: number | null
  energy: number | null
  lampStatus: string | null
  collectedAt: number | null
}

export function getOverview(config?: AxiosRequestConfig): Promise<DashboardOverview> {
  return request.get('/dashboard/overview', config)
}

export function getHistory(deviceId: string, start: number, end: number, config?: AxiosRequestConfig): Promise<HistoryVO> {
  return request.get('/light/history', { ...config, params: { ...config?.params, deviceId, start, end } })
}

export function getCurrentLight(deviceId: string, config?: AxiosRequestConfig): Promise<CurrentLight> {
  return request.get(`/devices/${deviceId}/light`, config)
}

export function listLatestDeviceHealth(config?: AxiosRequestConfig): Promise<DeviceHealthReport[]> {
  return request.get('/devices/health/latest', config)
}

export function getDeviceHealthHistory(deviceId: string, config?: AxiosRequestConfig): Promise<DeviceHealthReport[]> {
  return request.get(`/devices/${deviceId}/health`, config)
}

export function evaluateDeviceHealth(deviceId: string, config?: AxiosRequestConfig): Promise<DeviceHealthReport> {
  return request.post(`/devices/${deviceId}/health/evaluate`, undefined, config)
}
