<!-- ====== 全局提示组件 ====== -->
<template>
  <div class="toast" :class="{ show: toastVisible }" role="status" aria-live="polite" :aria-hidden="!toastVisible">
    {{ toastMessage }}
  </div>
</template>

<script setup lang="ts">
import { useUiStore } from '@/stores/ui'
import { storeToRefs } from 'pinia'

const { toastMessage, toastVisible } = storeToRefs(useUiStore())
</script>

<style scoped>
.toast {
  position: fixed;
  top: 30px;
  left: 50%;
  transform: translateX(-50%) translateY(-80px);
  background: var(--bg-panel);
  border: 1px solid rgba(242, 169, 0, 0.4);
  color: var(--text-primary);
  padding: 12px 24px;
  border-radius: 10px;
  font-size: 0.875rem;
  z-index: 300;
  opacity: 0;
  transition: all 0.3s ease;
  pointer-events: none;
  max-width: 80vw;
  white-space: normal;
  overflow-wrap: anywhere;
  max-height: 35dvh;
  overflow: auto;
  box-shadow: 0 6px 30px #283c3530;
}

.toast.show {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}
</style>
