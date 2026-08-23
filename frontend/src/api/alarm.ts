import request from './request'

/**
 * 告警管理（功能清单 F-08 / F-09）
 * 以下接口为"约定接口"，后端尚未实现（见 API.md），调用失败由页面降级处理。
 */

export type AlarmLevel = 'info' | 'warning' | 'critical'
export type AlarmStatus = 'OPEN' | 'ACKED'

export interface AlarmVO {
  id: number
  deviceId: string
  type: string
  level: AlarmLevel
  message: string
  ts: number
  status: AlarmStatus
}

/** 告警记录列表 F-09：GET /api/alarms */
export function listAlarms(): Promise<AlarmVO[]> {
  return request.get('/alarms', { silent: true })
}

/** 确认告警 F-08：POST /api/alarms/{id}/ack */
export function ackAlarm(id: number): Promise<null> {
  return request.post(`/alarms/${id}/ack`, null, { silent: true })
}
