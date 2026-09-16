<template>
  <div class="branch-choices">
    <p v-if="event.resolved" role="status">事件已结束</p>
    <p v-else-if="!canChoose" role="status">等待房主选择，全队共同结算</p>
    <div v-for="option in event.options" :key="option.id" class="branch-option">
      <button type="button" class="btn-primary" :disabled="busy || !canChoose || !option.enabled || (event.resolved && option.id !== 'leave')" @click="$emit('choose', option.id)">
        {{ option.label }}
      </button>
      <p v-if="option.disabledReason">{{ option.disabledReason }}</p>
      <small v-for="(member, userId) in option.members" :key="userId">
        {{ userId }}：生命 -{{ member.hpCost }}，金币 -{{ member.goldCost }}<template v-if="member.targetName">，目标：{{ member.targetName }}</template>
      </small>
    </div>
  </div>
</template>
<script setup lang="ts">
import type { EventPreview } from '@/types'
withDefaults(defineProps<{ event: EventPreview; busy?: boolean; canChoose?: boolean }>(), { canChoose: true })
defineEmits<{ choose: [optionId: string] }>()
</script>
<style scoped>
.branch-choices { display: grid; gap: 12px; }
.branch-option button { width: 100%; min-height: 48px; white-space: normal; }
.branch-option small { display: block; margin-top: 4px; overflow-wrap: anywhere; }
.branch-option p { margin: 4px 0; }
</style>
