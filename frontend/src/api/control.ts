import request from './request'

/**
 * 设备控制（功能清单 F-05 / F-06 / F-07）
 * 接口已由 Java 后端实现；调用失败时由页面降级处理。
 */

export interface BrightnessPeriod {
  name: string
  startTime: string
  brightnessPercent: number
}

/** 光照联动配置：Lux 只负责开关，时间段只负责开灯后的亮度百分比。 */
export interface LinkageConfig {
  enabled: boolean
  threshold: number
  hysteresis: number
  brightnessPeriods: BrightnessPeriod[]
  currentBrightnessPercent?: number
  currentBrightnessPeriod?: string
  currentTime?: string
}

/** 手动开关命令状态（后端 CommandStatus 枚举） */
export type CommandStatus = 'DISPATCHED' | 'ACKED' | 'SUCCESS' | 'FAILED' | 'TIMEOUT'

/** 控制命令结果（后端 ControlResultDTO）：DISPATCHED=已发送 ACKED=设备已接收 SUCCESS=执行成功 FAILED=执行失败 TIMEOUT=等待超时 */
export interface ControlResult {
  commandId: string
  deviceId: string
  action: 'ON' | 'OFF'
  mode: string
  status: CommandStatus
  issuedAt: number | null
  message: string | null
}

/** 手动远程开关灯 F-06：POST /api/devices/{deviceId}/switch，返回命令 ID 与初始状态 */
export function switchLight(deviceId: string, on: boolean): Promise<ControlResult> {
  return request.post(`/devices/${deviceId}/switch`, { on }, { silent: true })
}

/** 查询命令执行状态：GET /api/devices/commands/{commandId}（前端轮询命令流转用） */
export function getCommandStatus(commandId: string): Promise<ControlResult> {
  return request.get(`/devices/commands/${commandId}`, { silent: true })
}

/** 查询联动与阈值配置 F-05/F-07：GET /api/config/linkage */
export function getLinkageConfig(): Promise<LinkageConfig> {
  return request.get('/config/linkage', { silent: true })
}

/** 保存联动与阈值配置 F-05/F-07：PUT /api/config/linkage */
export function saveLinkageConfig(config: LinkageConfig): Promise<null> {
  return request.put('/config/linkage', config, { silent: true })
}
