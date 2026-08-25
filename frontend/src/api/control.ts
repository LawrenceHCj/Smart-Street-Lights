import request from './request'

/**
 * 设备控制（功能清单 F-05 / F-06 / F-07）
 * 接口已由 Java 后端实现；调用失败时由页面降级处理。
 */

/** 光照联动配置：联动总开关 + 开关阈值（Lux） */
export interface LinkageConfig {
  enabled: boolean
  threshold: number
}

/** 手动远程开关灯 F-06：POST /api/devices/{deviceId}/switch */
export function switchLight(deviceId: string, on: boolean): Promise<null> {
  return request.post(`/devices/${deviceId}/switch`, { on }, { silent: true })
}

/** 查询联动与阈值配置 F-05/F-07：GET /api/config/linkage */
export function getLinkageConfig(): Promise<LinkageConfig> {
  return request.get('/config/linkage', { silent: true })
}

/** 保存联动与阈值配置 F-05/F-07：PUT /api/config/linkage */
export function saveLinkageConfig(config: LinkageConfig): Promise<null> {
  return request.put('/config/linkage', config, { silent: true })
}
