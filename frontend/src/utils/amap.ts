type AMapApi = unknown

declare global {
  interface Window {
    AMap?: AMapApi
    _AMapSecurityConfig?: { securityJsCode?: string }
  }
}

let loading: Promise<AMapApi> | null = null

export function loadAMap(): Promise<AMapApi> {
  if (window.AMap) return Promise.resolve(window.AMap)
  if (loading) return loading
  const key = import.meta.env.VITE_AMAP_KEY
  if (!key) return Promise.reject(new Error('未配置 VITE_AMAP_KEY'))
  const securityJsCode = import.meta.env.VITE_AMAP_SECURITY_JS_CODE
  if (securityJsCode) window._AMapSecurityConfig = { securityJsCode }
  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}`
    script.async = true
    script.onload = () => window.AMap ? resolve(window.AMap) : reject(new Error('高德地图 SDK 加载失败'))
    script.onerror = () => reject(new Error('无法连接高德地图服务'))
    document.head.appendChild(script)
  }).catch((error) => {
    loading = null
    throw error
  })
  return loading
}
