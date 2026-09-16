<!-- ====== 根组件 ====== -->
<template>
  <router-view />
  <Toast />
  <ConfirmModal />
  <StoryPanel />
</template>

<script setup lang="ts">
import Toast from '@/components/Toast.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import StoryPanel from '@/components/StoryPanel.vue'
</script>
