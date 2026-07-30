<!-- ====== 确认弹窗组件 ====== -->
<template>
  <div v-if="store.confirmVisible" class="modal-overlay" @click.self="store.confirmCancel">
    <div class="modal-box">
      <h3>{{ store.confirmOptions.title }}</h3>
      <p v-if="store.confirmOptions.message" style="white-space: pre-line;">{{ store.confirmOptions.message }}</p>
      <div class="modal-actions">
        <button
          v-if="store.confirmOptions.showDelete"
          class="btn-delete"
          @click="store.confirmDelete"
        >
          {{ store.confirmOptions.deleteText || '删除存档' }}
        </button>
        <button class="btn-cancel" @click="store.confirmCancel">
          {{ store.confirmOptions.cancelText || '取消' }}
        </button>
        <button class="btn-primary" @click="store.confirmOk">
          {{ store.confirmOptions.okText || '确定' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUiStore } from '@/stores/ui'

const store = useUiStore()
</script>

<style scoped>
.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
  margin-top: 8px;
}

.btn-cancel {
  background: var(--bg-card);
  color: var(--text-secondary);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 14px 32px;
  font-size: 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--font-display);
  letter-spacing: 2px;
}

.btn-cancel:hover {
  background: #3a3650;
  color: var(--text-primary);
}

.btn-delete {
  background: transparent;
  color: var(--red);
  border: 1px solid var(--red);
  padding: 14px 32px;
  font-size: 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--font-display);
  letter-spacing: 2px;
}

.btn-delete:hover {
  background: rgba(232, 93, 117, 0.15);
}
</style>
