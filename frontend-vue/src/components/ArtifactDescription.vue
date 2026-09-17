<template>
  <span class="artifact-copy" :class="{ compact }">
    <span class="artifact-effect"><span class="copy-label">实战效果</span>{{ effect || '效果信息暂不可用' }}</span>
    <span v-if="lore" class="artifact-lore"><span class="copy-label">西行逸闻</span>{{ lore }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { artworkLore, type ArtworkKind } from '@/constants/artwork'
const props = withDefaults(defineProps<{ kind: ArtworkKind; name: string; effect?: string; compact?: boolean }>(), { compact: false })
const lore = computed(() => artworkLore(props.kind, props.name))
</script>

<style scoped>
.artifact-copy, .artifact-effect, .artifact-lore { display: block; }
.artifact-copy { text-align: left; line-height: 1.65; overflow-wrap: anywhere; }
.artifact-effect { margin: 10px 0; color: var(--text-primary); font-size: .875rem; }
.artifact-lore { margin: 10px 0 0; padding-top: 10px; border-top: 1px solid var(--line); color: var(--text-secondary); font-size: .8125rem; }
.copy-label { display: block; margin-bottom: 3px; font-size: .625rem; letter-spacing: .13em; font-weight: 700; color: var(--gold); }
.compact .artifact-effect, .compact .artifact-lore { font-size: .75rem; margin-top: 8px; }
.compact .artifact-lore { padding-top: 8px; }
</style>
