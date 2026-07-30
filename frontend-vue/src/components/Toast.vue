<!-- ====== 全局提示组件 ====== -->
<template>
  <div class="toast" :class="{ show: toastVisible }">
    {{ toastMessage }}
  </div>
</template>

<script setup lang="ts">
import { useUiStore } from '@/stores/ui'

const { toastMessage, toastVisible } = useUiStore()
</script>

<style scoped>
.toast {
  position: fixed;
  top: 30px;
  left: 50%;
  transform: translateX(-50%) translateY(-80px);
  background: rgba(26, 24, 37, 0.95);
  border: 1px solid rgba(242, 169, 0, 0.4);
  color: var(--text-primary);
  padding: 12px 24px;
  border-radius: 10px;
  font-size: 14px;
  z-index: 300;
  opacity: 0;
  transition: all 0.3s ease;
  pointer-events: none;
  max-width: 80vw;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.toast.show {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}
</style>
