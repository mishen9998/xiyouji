<!--
  战斗角色展示组件

  组件不依赖 Three.js 或外部模型运行：将现有角色立绘放入一个带
  perspective/transform-style 的舞台中，并通过 CSS 动画模拟待机、攻击、
  防御、能力和受击动作。后续如果接入 glTF/模型，只需将 imageUrl 替换为
  模型画布或 slot 内容，不需要改变动作状态 API。
-->
<template>
  <div
    class="battle-character"
    :class="[
      `battle-character--${size}`,
      // 同时保留语义动作类（ability）和视觉别名类（power），方便外部主题覆写。
      `battle-character--${action}`,
      `battle-character--${visualAction}`,
      `action-${action}`,
      { 'battle-character--failed': imageFailed },
    ]"
    role="img"
    :aria-label="ariaLabel"
    :data-action="action"
  >
    <!-- key 变化会让连续两次相同动作也重新播放动画 -->
    <div class="battle-character__stage" :key="animationKey">
      <div class="battle-character__ground" aria-hidden="true" />
      <div class="battle-character__aura" aria-hidden="true" />
      <div class="battle-character__model">
        <img
          v-if="imageUrl && !imageFailed"
          class="battle-character__image"
          :src="imageUrl"
          :alt="label || '战斗角色'"
          draggable="false"
          @error="onImageError"
        />
        <span v-else class="battle-character__emoji" aria-hidden="true">{{ emoji }}</span>
      </div>

      <!-- 动作特效均为装饰层，不影响点击和布局 -->
      <span class="battle-character__trail" aria-hidden="true" />
      <span class="battle-character__shield" aria-hidden="true">🛡️</span>
      <span class="battle-character__spark" aria-hidden="true">✦</span>
      <span class="battle-character__hit-flash" aria-hidden="true" />
    </div>

    <div v-if="showActionLabel && action !== 'idle'" class="battle-character__action-label">
      {{ actionLabel }}
    </div>
    <div v-if="label" class="battle-character__name">{{ label }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

/** ability 是产品文案使用的名称，power 保留作为后端 CardType.POWER 的别名。 */
export type BattleAction = 'idle' | 'attack' | 'defense' | 'ability' | 'power' | 'hit'
export type BattleCharacterSize = 'sm' | 'md' | 'lg'

const props = withDefaults(
  defineProps<{
    /** WebGL 不可用时使用的角色立绘降级图 */
    imageUrl?: string | null
    /** 没有图片时使用的角色 emoji */
    emoji?: string
    /** 当前动作；不传时保持静立 */
    action?: BattleAction
    /** 用于重复触发同一种动作（例如连续两次攻击） */
    actionKey?: string | number
    /** actionKey 的语义化别名；传入时优先使用它 */
    actionToken?: string | number
    /** 角色名，作为无障碍标签和可选名称 */
    label?: string
    size?: BattleCharacterSize
    showActionLabel?: boolean
  }>(),
  {
    imageUrl: null,
    emoji: '🦸',
    action: 'idle',
    actionKey: 0,
    actionToken: undefined,
    label: '',
    size: 'md',
    showActionLabel: true,
  },
)

const imageFailed = ref(false)

watch(
  () => props.imageUrl,
  () => {
    // 切换角色后重新尝试加载图片
    imageFailed.value = false
  },
)

const action = computed<BattleAction>(() => props.action || 'idle')
// ability/power 使用同一套视觉动画，外部可以按业务语义选择任一名称。
const visualAction = computed(() => action.value === 'ability' ? 'power' : action.value)
const animationKey = computed(() => `${action.value}:${String(props.actionToken ?? props.actionKey ?? 0)}`)
const actionLabel = computed(() => {
  const labels: Record<BattleAction, string> = {
    idle: '待机',
    attack: '攻击',
    defense: '防御',
    ability: '能力',
    power: '能力',
    hit: '受击',
  }
  return labels[action.value]
})
const ariaLabel = computed(() => {
  const name = props.label || '战斗角色'
  return action.value === 'idle' ? name : `${name}：${actionLabel.value}`
})

function onImageError() {
  imageFailed.value = true
}
</script>

<style scoped>
.battle-character {
  --character-width: 180px;
  --character-height: 250px;
  --character-accent: #f2a900;
  --character-glow: rgba(242, 169, 0, 0.38);
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  min-width: var(--character-width);
  color: var(--text-primary, #fffffe);
  contain: layout style;
}

.battle-character--sm {
  --character-width: 112px;
  --character-height: 158px;
}

.battle-character--lg {
  --character-width: 230px;
  --character-height: 320px;
}

.battle-character__stage {
  position: relative;
  width: var(--character-width);
  height: calc(var(--character-height) + 24px);
  perspective: 760px;
  perspective-origin: 50% 46%;
  isolation: isolate;
}

.battle-character__ground {
  position: absolute;
  z-index: -1;
  left: 8%;
  right: 8%;
  bottom: 4px;
  height: 18px;
  border-radius: 50%;
  background: radial-gradient(ellipse, rgba(0, 0, 0, 0.7), transparent 72%);
  filter: blur(2px);
  transform: rotateX(68deg) translateZ(-8px);
  transform-origin: center;
}

.battle-character__aura {
  position: absolute;
  z-index: -1;
  left: 8%;
  top: 10%;
  width: 84%;
  height: 78%;
  border-radius: 50%;
  opacity: 0.34;
  background: radial-gradient(ellipse, var(--character-glow), transparent 68%);
  filter: blur(8px);
  transform: translateZ(-36px) rotateX(8deg);
  transition: opacity 180ms ease, filter 180ms ease;
}

.battle-character__model {
  position: absolute;
  left: 8%;
  right: 8%;
  top: 0;
  bottom: 12px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  overflow: visible;
  transform-style: preserve-3d;
  transform: rotateY(-7deg) rotateX(1deg) translateZ(8px);
  transform-origin: 50% 88%;
  filter: drop-shadow(0 14px 10px rgba(0, 0, 0, 0.4));
}

/* 立绘背面层制造轻量的“厚度”，即使使用 2D 图片也有 3D 纵深感 */
.battle-character__model::before,
.battle-character__model::after {
  content: '';
  position: absolute;
  z-index: -1;
  inset: 7% 10% 3%;
  border-radius: 44% 44% 18% 18%;
  pointer-events: none;
}

.battle-character__model::before {
  background: linear-gradient(110deg, rgba(255, 255, 255, 0.16), transparent 36%, rgba(0, 0, 0, 0.32));
  transform: translateZ(-12px) translateX(8px) scale(0.96);
  filter: blur(1px);
}

.battle-character__model::after {
  border: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: inset 0 0 22px rgba(255, 255, 255, 0.08), 0 0 24px var(--character-glow);
  transform: translateZ(-5px) scale(1.02);
  opacity: 0.64;
}

.battle-character__image {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top center;
  border: 2px solid var(--character-accent);
  border-color: color-mix(in srgb, var(--character-accent) 82%, white 18%);
  border-radius: 22px 22px 14px 14px;
  background: linear-gradient(160deg, #3a3650, #1a1825);
  backface-visibility: hidden;
  user-select: none;
}

.battle-character__emoji {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 100%;
  height: 78%;
  border: 2px solid var(--character-accent);
  border-color: color-mix(in srgb, var(--character-accent) 82%, white 18%);
  border-radius: 22px 22px 14px 14px;
  background: linear-gradient(160deg, #3a3650, #1a1825);
  font-size: clamp(60px, 10vw, 100px);
}

.battle-character__trail,
.battle-character__shield,
.battle-character__spark,
.battle-character__hit-flash {
  position: absolute;
  pointer-events: none;
  opacity: 0;
}

.battle-character__trail {
  z-index: 3;
  top: 34%;
  right: -10%;
  width: 62%;
  height: 12%;
  border-radius: 50%;
  border-top: 4px solid rgba(232, 93, 117, 0.92);
  border-bottom: 2px solid rgba(242, 169, 0, 0.55);
  filter: blur(1px) drop-shadow(0 0 7px rgba(232, 93, 117, 0.75));
  transform: rotate(-14deg) translateZ(28px);
}

.battle-character__shield {
  z-index: 3;
  left: -7%;
  top: 32%;
  font-size: clamp(30px, 5vw, 54px);
  filter: drop-shadow(0 0 10px rgba(79, 195, 247, 0.8));
}

.battle-character__spark {
  z-index: 3;
  right: -4%;
  top: 18%;
  color: #e6c7ff;
  font-size: clamp(30px, 5vw, 52px);
  text-shadow: 0 0 12px rgba(187, 134, 252, 0.95);
}

.battle-character__hit-flash {
  z-index: 4;
  inset: 4% 5% 12%;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.8);
  mix-blend-mode: screen;
}

.battle-character__action-label {
  min-height: 18px;
  margin-top: -1px;
  color: var(--character-accent);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 2px;
  text-shadow: 0 0 8px var(--character-glow);
}

.battle-character__name {
  max-width: calc(var(--character-width) + 24px);
  overflow: hidden;
  margin-top: 3px;
  color: var(--text-secondary, #a7a9be);
  font-size: 12px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 待机：轻微呼吸和绕 Y 轴转动，避免角色显得像静态贴图 */
.battle-character--idle .battle-character__model {
  animation: character-idle 2.8s ease-in-out infinite;
}

.battle-character--idle .battle-character__aura {
  animation: aura-breathe 2.8s ease-in-out infinite;
}

.battle-character--attack .battle-character__model {
  animation: character-attack 620ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.battle-character--attack .battle-character__trail {
  animation: attack-trail 520ms ease-out 90ms both;
}

.battle-character--defense .battle-character__model {
  animation: character-defense 760ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.battle-character--defense .battle-character__shield {
  animation: defense-shield 760ms ease-out both;
}

.battle-character--power .battle-character__model {
  animation: character-power 860ms cubic-bezier(0.25, 0.8, 0.25, 1) both;
}

.battle-character--power .battle-character__aura {
  opacity: 0.86;
  animation: power-aura 860ms ease-out both;
}

.battle-character--power .battle-character__spark {
  animation: power-spark 780ms ease-out 80ms both;
}

.battle-character--hit .battle-character__model {
  animation: character-hit 560ms cubic-bezier(0.3, 0.1, 0.5, 1) both;
}

.battle-character--hit .battle-character__hit-flash {
  animation: hit-flash 560ms ease-out both;
}

.battle-character--hit .battle-character__aura {
  background: radial-gradient(ellipse, rgba(232, 93, 117, 0.64), transparent 68%);
  opacity: 0.85;
}

@keyframes character-idle {
  0%, 100% { transform: rotateY(-7deg) rotateX(1deg) translate3d(0, 0, 8px); }
  50% { transform: rotateY(5deg) rotateX(0deg) translate3d(0, -7px, 14px); }
}

@keyframes aura-breathe {
  0%, 100% { transform: translateZ(-36px) rotateX(8deg) scale(0.94); }
  50% { transform: translateZ(-36px) rotateX(8deg) scale(1.06); }
}

@keyframes character-attack {
  0% { transform: rotateY(-7deg) translate3d(0, 0, 8px) scale(1); }
  28% { transform: rotateY(-19deg) translate3d(-9px, -4px, 16px) scale(1.04); }
  58% { transform: rotateY(18deg) translate3d(40px, -2px, 42px) scale(1.08); }
  100% { transform: rotateY(-7deg) translate3d(0, 0, 8px) scale(1); }
}

@keyframes attack-trail {
  0% { opacity: 0; transform: rotate(-14deg) translate3d(-34px, 8px, 0) scaleX(0.2); }
  46% { opacity: 1; }
  100% { opacity: 0; transform: rotate(-14deg) translate3d(18px, 0, 28px) scaleX(1.15); }
}

@keyframes character-defense {
  0% { transform: rotateY(-7deg) translate3d(0, 0, 8px) scale(1); }
  35% { transform: rotateY(-20deg) translate3d(-12px, 4px, 18px) scale(0.95, 1.04); }
  68% { transform: rotateY(13deg) translate3d(5px, 0, 26px) scale(0.98, 1.02); }
  100% { transform: rotateY(-7deg) translate3d(0, 0, 8px) scale(1); }
}

@keyframes defense-shield {
  0% { opacity: 0; transform: translate3d(-18px, 8px, 20px) scale(0.55) rotate(-20deg); }
  36% { opacity: 1; transform: translate3d(0, 0, 35px) scale(1.08) rotate(0deg); }
  100% { opacity: 0; transform: translate3d(8px, -3px, 44px) scale(1.2) rotate(12deg); }
}

@keyframes character-power {
  0% { transform: rotateY(-7deg) translate3d(0, 0, 8px) scale(0.96); }
  45% { transform: rotateY(8deg) translate3d(0, -13px, 30px) scale(1.06); }
  100% { transform: rotateY(-7deg) translate3d(0, 0, 8px) scale(1); }
}

@keyframes power-aura {
  0% { opacity: 0.24; transform: translateZ(-36px) scale(0.72) rotate(0deg); }
  55% { opacity: 0.95; transform: translateZ(-36px) scale(1.15) rotate(180deg); }
  100% { opacity: 0.34; transform: translateZ(-36px) scale(0.96) rotate(360deg); }
}

@keyframes power-spark {
  0% { opacity: 0; transform: translate3d(-4px, 20px, 30px) scale(0.2) rotate(0deg); }
  55% { opacity: 1; transform: translate3d(0, -12px, 48px) scale(1.25) rotate(180deg); }
  100% { opacity: 0; transform: translate3d(8px, -28px, 30px) scale(0.2) rotate(360deg); }
}

@keyframes character-hit {
  0%, 100% { transform: rotateY(-7deg) translate3d(0, 0, 8px); filter: brightness(1) saturate(1); }
  15% { transform: rotateY(-7deg) translate3d(-11px, 1px, 8px); filter: brightness(1.75) saturate(0.7); }
  30% { transform: rotateY(8deg) translate3d(12px, -1px, 14px); filter: brightness(0.72) saturate(1.4); }
  48% { transform: rotateY(-5deg) translate3d(-7px, 0, 8px); }
  66% { transform: rotateY(4deg) translate3d(5px, 0, 8px); }
}

@keyframes hit-flash {
  0%, 10% { opacity: 0.9; }
  34% { opacity: 0; }
  100% { opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .battle-character__model,
  .battle-character__aura,
  .battle-character__trail,
  .battle-character__shield,
  .battle-character__spark,
  .battle-character__hit-flash {
    animation-duration: 1ms !important;
    animation-iteration-count: 1 !important;
  }
}
</style>
