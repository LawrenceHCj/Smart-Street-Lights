import request from './request'

/**
 * 告警管理（功能清单 F-08 / F-09）
 * 接口已由 Java 后端实现；调用失败时由页面降级处理。
 */

export type AlarmLevel = 'info' | 'warning' | 'critical'
export type AlarmStatus = 'OPEN' | 'ACKED'

/** 展示层派生状态：在原 OPEN/ACKED 基础上增加「已恢复」与「已关闭」 */
export type AlarmGroupStatus = AlarmStatus | 'RECOVERED' | 'CLOSED'

export interface AlarmVO {
  id: number
  deviceId: string
  type: string
  level: AlarmLevel
  message: string
  ts: number
  status: AlarmStatus
}

/** 按「设备+类型」聚合后的告警条目（去重并统计发生情况） */
export interface AlarmGroup {
  deviceId: string
  type: string
  level: AlarmLevel
  message: string
  /** 首次发生时间戳 */
  firstTs: number
  /** 最后发生时间戳 */
  lastTs: number
  /** 发生次数 */
  count: number
  /** 派生状态（由最新一条原始状态 + 设备在线情况得出） */
  status: AlarmGroupStatus
  /** 组内仍为 OPEN 的行 id（批量确认用） */
  openIds: number[]
  /** 最新一条原始告警 */
  latest: AlarmVO
}

/** 告警记录列表 F-09：GET /api/alarms */
export function listAlarms(): Promise<AlarmVO[]> {
  return request.get('/alarms', { silent: true })
}

/** 确认告警 F-08：POST /api/alarms/{id}/ack */
export function ackAlarm(id: number): Promise<null> {
  return request.post(`/alarms/${id}/ack`, null, { silent: true })
}
