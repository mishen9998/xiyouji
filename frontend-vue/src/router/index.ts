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
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
  },
  {
    path: '/char-select',
    name: 'char-select',
    component: () => import('@/views/CharacterSelectView.vue'),
  },
  {
    path: '/map',
    name: 'map',
    component: () => import('@/views/MapView.vue'),
  },
  {
    path: '/battle',
    name: 'battle',
    component: () => import('@/views/BattleView.vue'),
  },
  {
    path: '/room',
    name: 'room',
    component: () => import('@/views/RoomView.vue'),
  },
  {
    path: '/room/:code/battle',
    name: 'mp-battle',
    component: () => import('@/views/MultiplayerBattleView.vue'),
  },
  {
    path: '/room/:code/map',
    name: 'mp-map',
    component: () => import('@/views/MultiplayerMapView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
