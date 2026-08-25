/**
 * 接口探针：功能清单中的控制 / 告警 / 设备管理 / 用户管理接口，
 * 调用方用 probe() 包裹，
 * 成功拿到真实数据，失败则进入"服务未就绪"降级态，由页面展示 NotReadyBanner。
 */

export interface Probe<T> {
  /** 后端接口是否就绪：false 表示未实现或调用失败，data 为降级兜底值 */
  ready: boolean
  data: T
}

export async function probe<T>(p: Promise<T>, fallback: T): Promise<Probe<T>> {
  try {
    return { ready: true, data: await p }
  } catch {
    return { ready: false, data: fallback }
  }
}
