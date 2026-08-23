import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import '@fontsource-variable/inter'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/index.css'
import App from './App.vue'
import router from './router'

// 深色控制台：全局启用 Element Plus 深色主题
document.documentElement.classList.add('dark')

createApp(App).use(router).use(ElementPlus).mount('#app')
