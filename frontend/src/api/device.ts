import request from './request'

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

export function listDevices(): Promise<DeviceVO[]> {
  return request.get('/devices')
}

export function getOverview(): Promise<DashboardOverview> {
  return request.get('/dashboard/overview')
}

export function getHistory(deviceId: string, start: number, end: number): Promise<HistoryVO> {
  return request.get('/light/history', { params: { deviceId, start, end } })
}

export function getCurrentLight(deviceId: string): Promise<CurrentLight> {
  return request.get(`/devices/${deviceId}/light`)
}
