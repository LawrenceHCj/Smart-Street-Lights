import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
    {
      path: '/',
      component: MainLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '运行概览' } },
        { path: 'monitor', name: 'monitor', component: () => import('../views/Monitor.vue'), meta: { title: '实时监控' } },
        { path: 'control', name: 'control', component: () => import('../views/Control.vue'), meta: { title: '灯控管理' } },
        { path: 'alarms', name: 'alarms', component: () => import('../views/Alarms.vue'), meta: { title: '告警中心' } },
        { path: 'devices', name: 'devices', component: () => import('../views/DeviceManage.vue'), meta: { title: '设备管理' } },
        { path: 'users', name: 'users', component: () => import('../views/Users.vue'), meta: { title: '用户权限' } },
        { path: 'chat', name: 'chat', component: () => import('../views/Chat.vue'), meta: { title: '智能问答' } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? `${to.meta.title} · 智慧路灯` : '智慧路灯管理系统'
  document.title = title
})

export default router
