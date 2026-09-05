// ====== Vue Router 配置 ======
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/index.html',
    redirect: '/',
  },
  {
    path: '/',
    name: 'auth',
    component: () => import('@/views/AuthView.vue'),
  },
  {
    path: '/menu',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/char-select',
    name: 'char-select',
    component: () => import('@/views/CharacterSelectView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/map',
    name: 'map',
    component: () => import('@/views/MapView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/battle',
    name: 'battle',
    component: () => import('@/views/BattleView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/room',
    name: 'room',
    component: () => import('@/views/RoomView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/room/:code/battle',
    name: 'mp-battle',
    component: () => import('@/views/MultiplayerBattleView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/room/:code/map',
    name: 'mp-map',
    component: () => import('@/views/MultiplayerMapView.vue'),
    meta: { requiresAuth: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (!to.meta.requiresAuth) return true
  try {
    return localStorage.getItem('xiyouji_jwt_token') ? true : { name: 'auth' }
  } catch {
    return { name: 'auth' }
  }
})

export default router
