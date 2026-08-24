import request from './request'

/**
 * 设备管理（功能清单 F-10）
 * 设备列表查询复用 device.ts 的 listDevices()。
 */

export interface AddDevicePayload {
  code: string
  location: string
  longitude: number
  latitude: number
}

/** 添加设备 F-10：POST /api/devices */
export function addDevice(payload: AddDevicePayload): Promise<import('./device').DeviceVO> {
  return request.post('/devices', payload, { silent: true })
}

/** 解绑设备 F-10：DELETE /api/devices/{deviceId} */
export function removeDevice(deviceId: string): Promise<null> {
  return request.delete(`/devices/${deviceId}`, { silent: true })
}
