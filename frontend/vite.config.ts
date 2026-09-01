import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.SMARTLAMP_API_TARGET || 'http://localhost:8080'
  return {
    plugins: [vue()],
    server: {
      host: true, // 局域网可访问：同事通过 http://<本机IP>:5173 打开
      port: 5173,
      proxy: {
        // 本地开发代理到后端，避免跨域
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        },
      },
    },
    preview: {
      host: true,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
