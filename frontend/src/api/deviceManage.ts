import request from './request'

/**
 * 设备管理（功能清单 F-10）
 * 添加 / 解绑为"约定接口"，后端尚未实现（见 API.md），调用失败由页面降级处理。
 * 设备列表查询复用 device.ts 的 listDevices()（已实现）。
 */

export interface AddDevicePayload {
  code: string
  location: string
}

/** 添加设备 F-10：POST /api/devices */
export function addDevice(payload: AddDevicePayload): Promise<null> {
  return request.post('/devices', payload, { silent: true })
}

/** 解绑设备 F-10：DELETE /api/devices/{deviceId} */
export function removeDevice(deviceId: string): Promise<null> {
  return request.delete(`/devices/${deviceId}`, { silent: true })
}
