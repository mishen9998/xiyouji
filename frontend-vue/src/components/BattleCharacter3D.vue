<!-- 程序化 WebGL 3D 战斗角色；WebGL 不可用时自动降级到立绘动画。 -->
<template>
  <div
    class="character-3d"
    :class="[`character-3d--${size}`, `character-3d--${action}`]"
    :aria-label="`${label || '战斗角色'}：${actionLabel}`"
  >
    <div v-show="webglReady" ref="host" class="character-3d__viewport" aria-hidden="true"></div>
    <BattleCharacter
      v-if="webglFailed"
      :image-url="imageUrl"
      :emoji="emoji"
      :action="action"
      :action-token="resolvedToken"
      :label="label"
      :size="size"
      :show-action-label="false"
    />
    <div v-if="webglReady" class="character-3d__plate">
      <span class="character-3d__sigil">{{ emoji }}</span>
      <span>{{ label }}</span>
    </div>
    <div v-if="showActionLabel && action !== 'idle'" class="character-3d__action">
      {{ actionLabel }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import BattleCharacter from './BattleCharacter.vue'
import type { BattleAction, BattleCharacterSize } from './BattleCharacter.vue'

const props = withDefaults(defineProps<{
  characterClass?: string
  imageUrl?: string | null
  emoji?: string
  action?: BattleAction
  actionKey?: string | number
  actionToken?: string | number
  label?: string
  size?: BattleCharacterSize
  showActionLabel?: boolean
}>(), {
  characterClass: 'SUN_WUKONG',
  imageUrl: null,
  emoji: '🦸',
  action: 'idle',
  actionKey: 0,
  actionToken: undefined,
  label: '',
  size: 'md',
  showActionLabel: true,
})

const host = ref<HTMLElement | null>(null)
const webglReady = ref(false)
const webglFailed = ref(false)
const resolvedToken = computed(() => props.actionToken ?? props.actionKey)
const actionLabel = computed(() => ({
  idle: '待机', attack: '攻击', defense: '防御', ability: '能力', power: '能力', hit: '受击',
}[props.action] ?? '待机'))

interface CharacterRig {
  root: THREE.Group
  torso: THREE.Object3D
  head: THREE.Object3D
  leftArm: THREE.Object3D
  rightArm: THREE.Object3D
  leftLeg: THREE.Object3D
  rightLeg: THREE.Object3D
  aura: THREE.Mesh<THREE.RingGeometry, THREE.MeshBasicMaterial>
  skeleton?: THREE.Skeleton
  sun?: {
    pelvis: THREE.Bone
    spine: THREE.Bone
    chest: THREE.Bone
    neck: THREE.Bone
    head: THREE.Bone
    leftUpperArm: THREE.Bone
    leftForearm: THREE.Bone
    leftHand: THREE.Bone
    rightUpperArm: THREE.Bone
    rightForearm: THREE.Bone
    rightHand: THREE.Bone
    leftThigh: THREE.Bone
    leftShin: THREE.Bone
    leftFoot: THREE.Bone
    rightThigh: THREE.Bone
    rightShin: THREE.Bone
    rightFoot: THREE.Bone
    tail: THREE.Bone[]
    cape: THREE.Group
    scarf: THREE.Group
    staff: THREE.Group
  }
}

let scene: THREE.Scene | undefined
let camera: THREE.PerspectiveCamera | undefined
let renderer: THREE.WebGLRenderer | undefined
let rig: CharacterRig | undefined
let frameId = 0
let resizeObserver: ResizeObserver | undefined
let startedAt = performance.now()

const palettes: Record<string, { primary: number; secondary: number; skin: number; metal: number }> = {
  SUN_WUKONG: { primary: 0xb5261e, secondary: 0xe8a420, skin: 0xb97845, metal: 0xffcf52 },
  ZHU_BAJIE: { primary: 0x294f82, secondary: 0x6a9bc3, skin: 0xe9a6a7, metal: 0xaab7c6 },
  SHA_SENG: { primary: 0x245766, secondary: 0xb77a38, skin: 0xa55b35, metal: 0xd7aa54 },
  BAI_LONGMA: { primary: 0xe9edf3, secondary: 0x67d7ea, skin: 0xf2f4f7, metal: 0x8be8ff },
  TANG_SANZANG: { primary: 0xb72b30, secondary: 0xe7a928, skin: 0xe7bd91, metal: 0xe8c968 },
}

function material(color: number, metalness = 0.1, roughness = 0.58) {
  return new THREE.MeshStandardMaterial({ color, metalness, roughness })
}

function mesh(geometry: THREE.BufferGeometry, mat: THREE.Material) {
  const value = new THREE.Mesh(geometry, mat)
  value.castShadow = true
  value.receiveShadow = true
  return value
}

function limb(color: number, length: number, radius = 0.13) {
  const group = new THREE.Group()
  const part = mesh(new THREE.CapsuleGeometry(radius, length, 6, 10), material(color))
  part.position.y = -length / 2
  group.add(part)
  return group
}

function createWeapon(characterClass: string, metal: number) {
  const weapon = new THREE.Group()
  const staff = mesh(new THREE.CylinderGeometry(0.045, 0.045, 2.25, 12), material(metal, 0.72, 0.24))
  staff.position.y = -0.86
  weapon.add(staff)

  if (characterClass === 'ZHU_BAJIE') {
    for (let i = -2; i <= 2; i++) {
      const tooth = mesh(new THREE.CylinderGeometry(0.022, 0.03, 0.42, 8), material(metal, 0.65, 0.28))
      tooth.position.set(i * 0.105, -1.96, 0)
      weapon.add(tooth)
    }
    staff.rotation.z = Math.PI / 12
  } else if (characterClass === 'TANG_SANZANG') {
    for (let i = 0; i < 3; i++) {
      const ring = mesh(new THREE.TorusGeometry(0.13 + i * 0.025, 0.018, 8, 24), material(metal, 0.7, 0.25))
      ring.position.set(0, -2.04 + i * 0.08, 0)
      ring.rotation.y = i * 0.72
      weapon.add(ring)
    }
  }
  return weapon
}

function goldMaterial() {
  return new THREE.MeshPhysicalMaterial({
    color: 0xd99a24,
    metalness: 0.88,
    roughness: 0.25,
    clearcoat: 0.45,
    clearcoatRoughness: 0.22,
  })
}

function addCapsule(parent: THREE.Object3D, length: number, radius: number, mat: THREE.Material, y = -length / 2) {
  const value = mesh(new THREE.CapsuleGeometry(radius, length, 7, 12), mat)
  value.position.y = y
  parent.add(value)
  return value
}

function addArmorPlate(
  parent: THREE.Object3D,
  position: THREE.Vector3Tuple,
  scale: THREE.Vector3Tuple,
  rotation: THREE.EulerTuple = [0, 0, 0],
  mat: THREE.Material = goldMaterial(),
) {
  const plate = mesh(new THREE.SphereGeometry(0.34, 16, 12), mat)
  plate.position.set(...position)
  plate.scale.set(...scale)
  plate.rotation.set(...rotation)
  parent.add(plate)
  return plate
}

/**
 * 以 sunwukong.png 的斗战胜佛造型为依据搭建的孙悟空骨骼模型。
 * 每个肢体、尾巴和武器都挂在 THREE.Bone 上，动作只驱动骨骼，不再
 * 直接摇晃胶囊体。盔甲使用刚性蒙皮思路附着到对应骨骼。
 */
function createSunWukongRig(): CharacterRig {
  const root = new THREE.Group()
  root.scale.setScalar(0.93)

  const dark = material(0x17151b, 0.34, 0.48)
  const darkCloth = material(0x272029, 0.04, 0.82)
  const red = material(0x8f1f1b, 0.18, 0.55)
  const brightRed = material(0xc33424, 0.16, 0.48)
  const fur = material(0x754326, 0.02, 0.9)
  const skin = material(0xd29a6d, 0.02, 0.78)
  const black = material(0x120f12, 0, 0.64)
  const white = material(0xffe8cc, 0, 0.48)
  const gold = goldMaterial()
  const ruby = new THREE.MeshPhysicalMaterial({ color: 0x9f1015, roughness: 0.25, clearcoat: 0.75 })

  const pelvis = new THREE.Bone()
  pelvis.name = 'pelvis'
  pelvis.position.y = 1.18
  root.add(pelvis)

  const spine = new THREE.Bone()
  spine.name = 'spine'
  spine.position.y = 0.18
  pelvis.add(spine)
  const chest = new THREE.Bone()
  chest.name = 'chest'
  chest.position.y = 0.54
  spine.add(chest)
  const neck = new THREE.Bone()
  neck.name = 'neck'
  neck.position.y = 0.56
  chest.add(neck)
  const head = new THREE.Bone()
  head.name = 'head'
  head.position.y = 0.2
  neck.add(head)

  const makeArm = (side: -1 | 1) => {
    const upper = new THREE.Bone()
    upper.name = side < 0 ? 'leftUpperArm' : 'rightUpperArm'
    upper.position.set(side * 0.49, 0.37, 0)
    chest.add(upper)
    addCapsule(upper, 0.48, 0.13, red)
    const shoulder = addArmorPlate(upper, [0, -0.03, 0], [0.86, 0.58, 0.84], [0, 0, side * 0.2], gold)
    const shoulderCore = mesh(new THREE.DodecahedronGeometry(0.17, 0), ruby)
    shoulderCore.position.set(side * 0.03, -0.02, 0.18)
    upper.add(shoulderCore)
    for (let i = 0; i < 3; i++) {
      const spike = mesh(new THREE.ConeGeometry(0.055, 0.28 - i * 0.035, 7), gold)
      spike.position.set(side * (0.18 + i * 0.07), 0.08 - i * 0.06, -0.02)
      spike.rotation.z = side * (-0.88 + i * 0.14)
      upper.add(spike)
    }
    shoulder.scale.multiplyScalar(1.02)

    const forearm = new THREE.Bone()
    forearm.name = side < 0 ? 'leftForearm' : 'rightForearm'
    forearm.position.y = -0.58
    upper.add(forearm)
    addCapsule(forearm, 0.42, 0.105, fur)
    const bracer = mesh(new THREE.CylinderGeometry(0.16, 0.12, 0.34, 10), gold)
    bracer.position.y = -0.25
    forearm.add(bracer)
    for (const y of [-0.1, -0.39]) {
      const ring = mesh(new THREE.TorusGeometry(0.14, 0.022, 7, 18), ruby)
      ring.rotation.x = Math.PI / 2
      ring.position.y = y
      forearm.add(ring)
    }

    const hand = new THREE.Bone()
    hand.name = side < 0 ? 'leftHand' : 'rightHand'
    hand.position.y = -0.5
    forearm.add(hand)
    const fist = mesh(new THREE.SphereGeometry(0.13, 12, 10), skin)
    fist.scale.set(0.86, 1.08, 0.86)
    fist.position.y = -0.08
    hand.add(fist)
    return { upper, forearm, hand }
  }

  const leftArm = makeArm(-1)
  const rightArm = makeArm(1)

  const makeLeg = (side: -1 | 1) => {
    const thigh = new THREE.Bone()
    thigh.name = side < 0 ? 'leftThigh' : 'rightThigh'
    thigh.position.set(side * 0.23, -0.02, 0)
    pelvis.add(thigh)
    addCapsule(thigh, 0.52, 0.18, darkCloth)
    const shin = new THREE.Bone()
    shin.name = side < 0 ? 'leftShin' : 'rightShin'
    shin.position.y = -0.62
    thigh.add(shin)
    addCapsule(shin, 0.48, 0.15, dark)
    const knee = addArmorPlate(shin, [0, 0.01, 0.12], [0.55, 0.45, 0.34], [0, 0, 0], gold)
    const kneeGem = mesh(new THREE.OctahedronGeometry(0.07, 0), ruby)
    kneeGem.position.set(0, 0.01, 0.24)
    shin.add(kneeGem)
    knee.rotation.x = -0.22

    const foot = new THREE.Bone()
    foot.name = side < 0 ? 'leftFoot' : 'rightFoot'
    foot.position.y = -0.58
    shin.add(foot)
    const boot = mesh(new THREE.BoxGeometry(0.3, 0.22, 0.5), dark)
    boot.position.set(0, -0.06, 0.12)
    boot.rotation.x = -0.08
    foot.add(boot)
    const toe = mesh(new THREE.ConeGeometry(0.12, 0.28, 5), gold)
    toe.rotation.x = Math.PI / 2
    toe.position.set(0, -0.07, 0.42)
    foot.add(toe)
    for (const y of [-0.43, -0.2]) {
      const bootBand = mesh(new THREE.TorusGeometry(0.155, 0.025, 7, 18), gold)
      bootBand.rotation.x = Math.PI / 2
      bootBand.position.y = y
      shin.add(bootBand)
    }
    return { thigh, shin, foot }
  }

  const leftLeg = makeLeg(-1)
  const rightLeg = makeLeg(1)

  // 黑金锁子甲主体、胸甲与红色内袍。
  const torsoShell = mesh(new THREE.CapsuleGeometry(0.4, 0.62, 8, 18), dark)
  torsoShell.position.y = 0.15
  torsoShell.scale.z = 0.78
  spine.add(torsoShell)
  const redTunic = mesh(new THREE.CylinderGeometry(0.35, 0.44, 0.62, 10), red)
  redTunic.position.y = 0.02
  spine.add(redTunic)
  const chestPlate = addArmorPlate(chest, [0, 0.13, 0.27], [1.12, 0.78, 0.28], [-0.1, 0, 0], gold)
  chestPlate.geometry.rotateX(0.12)
  const chestRuby = mesh(new THREE.OctahedronGeometry(0.105, 0), ruby)
  chestRuby.position.set(0, 0.13, 0.45)
  chest.add(chestRuby)
  const collar = mesh(new THREE.TorusGeometry(0.3, 0.055, 8, 24, Math.PI * 1.45), gold)
  collar.position.set(0, 0.42, 0.04)
  collar.rotation.set(Math.PI / 2, 0, Math.PI * 0.78)
  chest.add(collar)

  // 腰带、兽面扣和原画中的分片战裙。
  const belt = mesh(new THREE.TorusGeometry(0.4, 0.07, 10, 28), gold)
  belt.rotation.x = Math.PI / 2
  belt.position.y = 0.02
  pelvis.add(belt)
  const beltFace = mesh(new THREE.DodecahedronGeometry(0.15, 0), gold)
  beltFace.position.set(0, 0.02, 0.39)
  pelvis.add(beltFace)
  const beltGem = mesh(new THREE.OctahedronGeometry(0.07, 0), ruby)
  beltGem.position.set(0, 0.03, 0.52)
  pelvis.add(beltGem)
  for (let i = -3; i <= 3; i++) {
    const skirt = mesh(new THREE.BoxGeometry(0.16, i === 0 ? 0.82 : 0.66, 0.075), i % 2 === 0 ? gold : red)
    const angle = i * 0.26
    skirt.position.set(Math.sin(angle) * 0.34, -0.4, Math.cos(angle) * 0.22)
    skirt.rotation.set(0.12, angle, -angle * 0.2)
    pelvis.add(skirt)
    for (const y of [-0.23, -0.43, -0.62]) {
      const scale = mesh(new THREE.BoxGeometry(0.12, 0.09, 0.025), gold)
      scale.position.set(0, y, 0.05)
      skirt.add(scale)
    }
  }

  // 猴首：棕色毛发、浅色面罩、尖耳、眉眼和口鼻。
  const headMass = mesh(new THREE.SphereGeometry(0.35, 22, 18), fur)
  headMass.scale.set(0.92, 1.08, 0.9)
  head.add(headMass)
  const faceMask = mesh(new THREE.SphereGeometry(0.29, 20, 16), skin)
  faceMask.position.set(0, -0.035, 0.205)
  faceMask.scale.set(0.86, 0.88, 0.5)
  head.add(faceMask)
  const muzzle = mesh(new THREE.SphereGeometry(0.16, 16, 12), skin)
  muzzle.position.set(0, -0.16, 0.36)
  muzzle.scale.set(1.18, 0.68, 0.72)
  head.add(muzzle)
  const nose = mesh(new THREE.SphereGeometry(0.052, 10, 8), black)
  nose.position.set(0, -0.11, 0.48)
  nose.scale.set(1.25, 0.72, 0.68)
  head.add(nose)
  const mouth = mesh(new THREE.TorusGeometry(0.07, 0.012, 6, 18, Math.PI), black)
  mouth.position.set(0, -0.23, 0.47)
  mouth.rotation.z = Math.PI
  head.add(mouth)

  for (const side of [-1, 1] as const) {
    const ear = mesh(new THREE.SphereGeometry(0.14, 12, 10), fur)
    ear.position.set(side * 0.34, -0.005, 0.015)
    ear.scale.set(0.52, 1, 0.65)
    head.add(ear)
    const innerEar = mesh(new THREE.SphereGeometry(0.09, 10, 8), skin)
    innerEar.position.set(side * 0.37, -0.005, 0.055)
    innerEar.scale.set(0.38, 0.78, 0.42)
    head.add(innerEar)
    const eyeWhite = mesh(new THREE.SphereGeometry(0.07, 12, 9), white)
    eyeWhite.position.set(side * 0.115, 0.04, 0.42)
    eyeWhite.scale.set(1.16, 0.7, 0.42)
    eyeWhite.rotation.z = side * -0.22
    head.add(eyeWhite)
    const pupil = mesh(new THREE.SphereGeometry(0.026, 9, 7), black)
    pupil.position.set(side * 0.112, 0.035, 0.482)
    head.add(pupil)
    const brow = mesh(new THREE.BoxGeometry(0.15, 0.026, 0.035), black)
    brow.position.set(side * 0.115, 0.115, 0.445)
    brow.rotation.z = side * 0.27
    head.add(brow)
  }

  // 向后炸开的毛发与金红凤翅冠。
  for (let i = 0; i < 9; i++) {
    const angle = (i / 8 - 0.5) * 2.3
    const spike = mesh(new THREE.ConeGeometry(0.075, 0.4 + (i % 2) * 0.08, 7), fur)
    spike.position.set(Math.sin(angle) * 0.3, 0.2 + Math.cos(angle) * 0.19, -0.12)
    spike.rotation.z = -angle * 0.8
    spike.rotation.x = -0.22
    head.add(spike)
  }
  const crownBand = mesh(new THREE.TorusGeometry(0.3, 0.045, 8, 24, Math.PI * 1.25), gold)
  crownBand.position.set(0, 0.16, 0.03)
  crownBand.rotation.set(Math.PI / 2, 0, Math.PI * 0.88)
  head.add(crownBand)
  for (const side of [-1, 0, 1]) {
    const crownSpike = mesh(new THREE.ConeGeometry(side === 0 ? 0.075 : 0.055, side === 0 ? 0.55 : 0.42, 6), side === 0 ? ruby : gold)
    crownSpike.position.set(side * 0.12, 0.49 - Math.abs(side) * 0.035, 0.01)
    crownSpike.rotation.z = side * -0.18
    head.add(crownSpike)
  }
  for (const side of [-1, 1] as const) {
    const wing = mesh(new THREE.ConeGeometry(0.055, 0.54, 5), gold)
    wing.position.set(side * 0.3, 0.26, -0.01)
    wing.rotation.z = side * -1.02
    head.add(wing)
  }

  // 金箍棒带双端纹饰，绑定右手骨骼。
  const staff = new THREE.Group()
  staff.name = 'ruyijinguStaff'
  const staffCore = mesh(new THREE.CylinderGeometry(0.042, 0.042, 3.3, 16), gold)
  staff.add(staffCore)
  const staffShaft = mesh(
    new THREE.CylinderGeometry(0.047, 0.047, 2.52, 16),
    material(0xa96716, 0.76, 0.24),
  )
  staff.add(staffShaft)
  for (const y of [-1.52, -1.38, 1.38, 1.52]) {
    const ring = mesh(new THREE.TorusGeometry(0.07, 0.022, 8, 20), gold)
    ring.rotation.x = Math.PI / 2
    ring.position.y = y
    staff.add(ring)
  }
  staff.position.set(0, -0.18, 0.05)
  staff.rotation.z = 0.92
  rightArm.hand.add(staff)

  // 红色披风和飘带使用分节层级，能跟随胸骨摆动。
  const cape = new THREE.Group()
  cape.position.set(0, 0.35, -0.28)
  chest.add(cape)
  for (let i = 0; i < 3; i++) {
    const capePanel = mesh(new THREE.BoxGeometry(0.42, 1.28 - i * 0.13, 0.025), i === 1 ? brightRed : red)
    capePanel.position.set((i - 1) * 0.31, -0.57, -0.03 - Math.abs(i - 1) * 0.04)
    capePanel.rotation.z = (i - 1) * -0.16
    cape.add(capePanel)
    const trim = mesh(new THREE.BoxGeometry(0.035, 1.18 - i * 0.1, 0.032), gold)
    trim.position.set(capePanel.position.x + (i === 0 ? -0.19 : 0.19), capePanel.position.y, capePanel.position.z - 0.01)
    trim.rotation.z = capePanel.rotation.z
    cape.add(trim)
  }
  const scarf = new THREE.Group()
  scarf.position.set(0, 0.43, -0.05)
  chest.add(scarf)
  for (const side of [-1, 1] as const) {
    for (let i = 0; i < 3; i++) {
      const ribbon = mesh(new THREE.BoxGeometry(0.5, 0.09, 0.025), brightRed)
      ribbon.position.set(side * (0.34 + i * 0.38), 0.08 - i * 0.08, -0.12 - i * 0.04)
      ribbon.rotation.z = side * (-0.15 - i * 0.13)
      scarf.add(ribbon)
    }
  }

  // 四节尾骨，外观与原画的棕色卷尾一致。
  const tail: THREE.Bone[] = []
  let tailParent: THREE.Object3D = pelvis
  for (let i = 0; i < 4; i++) {
    const tailBone = new THREE.Bone()
    tailBone.name = `tail_${i}`
    tailBone.position.set(i === 0 ? 0 : 0, i === 0 ? -0.08 : -0.38, i === 0 ? -0.28 : 0)
    tailParent.add(tailBone)
    addCapsule(tailBone, 0.34, 0.09 - i * 0.012, fur)
    tail.push(tailBone)
    tailParent = tailBone
  }

  const auraMaterial = new THREE.MeshBasicMaterial({
    color: 0xe8a420,
    transparent: true,
    opacity: 0.2,
    side: THREE.DoubleSide,
    depthWrite: false,
  })
  const aura = new THREE.Mesh(new THREE.RingGeometry(0.86, 0.93, 64), auraMaterial)
  aura.position.set(0, 1.68, -0.62)
  root.add(aura)

  const bones = [
    pelvis, spine, chest, neck, head,
    leftArm.upper, leftArm.forearm, leftArm.hand,
    rightArm.upper, rightArm.forearm, rightArm.hand,
    leftLeg.thigh, leftLeg.shin, leftLeg.foot,
    rightLeg.thigh, rightLeg.shin, rightLeg.foot,
    ...tail,
  ]
  const skeleton = new THREE.Skeleton(bones)

  return {
    root,
    torso: chest,
    head,
    leftArm: leftArm.upper,
    rightArm: rightArm.upper,
    leftLeg: leftLeg.thigh,
    rightLeg: rightLeg.thigh,
    aura,
    skeleton,
    sun: {
      pelvis, spine, chest, neck, head,
      leftUpperArm: leftArm.upper,
      leftForearm: leftArm.forearm,
      leftHand: leftArm.hand,
      rightUpperArm: rightArm.upper,
      rightForearm: rightArm.forearm,
      rightHand: rightArm.hand,
      leftThigh: leftLeg.thigh,
      leftShin: leftLeg.shin,
      leftFoot: leftLeg.foot,
      rightThigh: rightLeg.thigh,
      rightShin: rightLeg.shin,
      rightFoot: rightLeg.foot,
      tail, cape, scarf, staff,
    },
  }
}

function createRig(characterClass: string): CharacterRig {
  if (characterClass === 'SUN_WUKONG') return createSunWukongRig()
  const colors = palettes[characterClass] ?? palettes.SUN_WUKONG
  const root = new THREE.Group()
  const torso = new THREE.Group()
  root.add(torso)

  const body = mesh(new THREE.CapsuleGeometry(0.42, 0.8, 8, 16), material(colors.primary))
  body.position.y = 1.48
  torso.add(body)

  const belt = mesh(new THREE.TorusGeometry(0.39, 0.07, 10, 24), material(colors.metal, 0.65, 0.25))
  belt.position.y = 1.16
  belt.rotation.x = Math.PI / 2
  torso.add(belt)

  const head = new THREE.Group()
  head.position.y = 2.42
  const face = mesh(new THREE.SphereGeometry(0.34, 20, 16), material(colors.skin))
  face.scale.set(0.9, 1.05, 0.88)
  head.add(face)
  torso.add(head)

  const eyeMat = material(0x17131d, 0, 0.3)
  for (const x of [-0.12, 0.12]) {
    const eye = mesh(new THREE.SphereGeometry(0.035, 10, 8), eyeMat)
    eye.position.set(x, 0.05, 0.3)
    head.add(eye)
  }

  const crown = mesh(
    new THREE.ConeGeometry(characterClass === 'TANG_SANZANG' ? 0.36 : 0.28, 0.52, characterClass === 'TANG_SANZANG' ? 8 : 5),
    material(colors.secondary, 0.5, 0.3),
  )
  crown.position.y = 0.42
  head.add(crown)

  if (characterClass === 'BAI_LONGMA') {
    for (const x of [-0.15, 0.15]) {
      const horn = mesh(new THREE.ConeGeometry(0.045, 0.48, 10), material(colors.metal, 0.45, 0.3))
      horn.position.set(x, 0.44, -0.02)
      horn.rotation.z = x * -1.4
      head.add(horn)
    }
    const snout = mesh(new THREE.CapsuleGeometry(0.12, 0.22, 5, 10), material(colors.skin))
    snout.rotation.x = Math.PI / 2
    snout.position.set(0, -0.1, 0.34)
    head.add(snout)
  }

  const leftArm = limb(colors.secondary, 0.86)
  leftArm.position.set(-0.48, 1.85, 0)
  leftArm.rotation.z = -0.2
  torso.add(leftArm)
  const rightArm = limb(colors.secondary, 0.86)
  rightArm.position.set(0.48, 1.85, 0)
  rightArm.rotation.z = 0.2
  torso.add(rightArm)
  rightArm.add(createWeapon(characterClass, colors.metal))

  const leftLeg = limb(colors.primary, 0.92, 0.15)
  leftLeg.position.set(-0.22, 1.0, 0)
  root.add(leftLeg)
  const rightLeg = limb(colors.primary, 0.92, 0.15)
  rightLeg.position.set(0.22, 1.0, 0)
  root.add(rightLeg)

  const shoulderGeo = new THREE.SphereGeometry(0.22, 12, 10)
  for (const x of [-0.49, 0.49]) {
    const shoulder = mesh(shoulderGeo, material(colors.metal, 0.5, 0.3))
    shoulder.scale.y = 0.68
    shoulder.position.set(x, 1.85, 0)
    torso.add(shoulder)
  }

  const auraMaterial = new THREE.MeshBasicMaterial({
    color: colors.secondary,
    transparent: true,
    opacity: 0.22,
    side: THREE.DoubleSide,
    depthWrite: false,
  })
  const aura = new THREE.Mesh(new THREE.RingGeometry(0.78, 0.86, 48), auraMaterial)
  aura.position.set(0, 1.55, -0.45)
  root.add(aura)
  return { root, torso, head, leftArm, rightArm, leftLeg, rightLeg, aura }
}

function setupScene() {
  if (!host.value) return
  try {
    scene = new THREE.Scene()
    camera = new THREE.PerspectiveCamera(34, 1, 0.1, 100)
    camera.position.set(0, 1.55, 7.2)
    camera.lookAt(0, 1.35, 0)

    renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFShadowMap
    host.value.replaceChildren(renderer.domElement)

    scene.add(new THREE.HemisphereLight(0xffefd1, 0x182039, 2.0))
    const keyLight = new THREE.DirectionalLight(0xffd47a, 3.2)
    keyLight.position.set(3, 5, 5)
    keyLight.castShadow = true
    scene.add(keyLight)
    const rim = new THREE.PointLight(0x8068ff, 2.6, 12)
    rim.position.set(-3, 2.8, 2)
    scene.add(rim)

    const floor = mesh(new THREE.CircleGeometry(1.25, 48), new THREE.MeshStandardMaterial({
      color: 0x2a2136, roughness: 0.85, metalness: 0.1, transparent: true, opacity: 0.72,
    }))
    floor.rotation.x = -Math.PI / 2
    floor.position.y = -0.02
    scene.add(floor)

    rebuildRig()
    resize()
    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(host.value)
    webglReady.value = true
    animate()
  } catch (error) {
    console.warn('WebGL 3D character unavailable, falling back to illustration:', error)
    webglFailed.value = true
  }
}

function disposeObject(root: THREE.Object3D) {
  root.traverse((child) => {
    if (child instanceof THREE.Mesh) {
      child.geometry.dispose()
      const materials = Array.isArray(child.material) ? child.material : [child.material]
      materials.forEach((value) => value.dispose())
    }
  })
}

function rebuildRig() {
  if (!scene) return
  if (rig) {
    scene.remove(rig.root)
    disposeObject(rig.root)
  }
  rig = createRig(props.characterClass)
  rig.root.rotation.y = -0.16
  scene.add(rig.root)
}

function resize() {
  if (!host.value || !renderer || !camera) return
  const width = Math.max(1, host.value.clientWidth)
  const height = Math.max(1, host.value.clientHeight)
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
}

function animateSunRig(now: number, phase: number, pulse: number) {
  if (!rig?.sun) return
  const s = rig.sun
  const idle = now / 1000

  rig.root.position.set(0, Math.sin(idle * 2.1) * 0.025, 0)
  rig.root.rotation.set(0, -0.12 + Math.sin(idle * 0.65) * 0.025, 0)
  s.pelvis.rotation.set(0, 0, 0)
  s.spine.rotation.set(Math.sin(idle * 2.1) * 0.018, 0, 0)
  s.chest.rotation.set(0, Math.sin(idle * 0.75) * 0.035, 0)
  s.neck.rotation.set(0, 0, 0)
  s.head.rotation.set(0, Math.sin(idle * 0.72) * 0.07, Math.sin(idle * 0.9) * 0.015)

  s.leftUpperArm.rotation.set(0.08, 0, -0.34 + Math.sin(idle * 2.1) * 0.025)
  s.leftForearm.rotation.set(0, 0, -0.46)
  s.leftHand.rotation.set(0, 0, 0.12)
  s.rightUpperArm.rotation.set(-0.08, 0, 0.28 - Math.sin(idle * 2.1) * 0.025)
  s.rightForearm.rotation.set(0, 0, 0.38)
  s.rightHand.rotation.set(0, 0, -0.12)

  s.leftThigh.rotation.set(0, 0, -0.045)
  s.leftShin.rotation.set(0.035, 0, 0)
  s.leftFoot.rotation.set(-0.035, 0, 0)
  s.rightThigh.rotation.set(0, 0, 0.045)
  s.rightShin.rotation.set(-0.035, 0, 0)
  s.rightFoot.rotation.set(0.035, 0, 0)

  s.staff.rotation.set(0.08, 0.04, 0.92)
  s.cape.rotation.set(-0.08 + Math.sin(idle * 1.35) * 0.035, Math.sin(idle * 0.8) * 0.04, Math.sin(idle * 1.1) * 0.025)
  s.scarf.rotation.set(0, Math.sin(idle * 1.2) * 0.07, Math.sin(idle * 1.5) * 0.04)
  s.tail.forEach((bone, index) => {
    bone.rotation.set(0.08, 0, -0.47 + Math.sin(idle * 1.7 - index * 0.6) * 0.2 + index * 0.12)
  })

  rig.aura.material.opacity = 0.15 + Math.sin(idle * 2.2) * 0.035
  rig.aura.scale.setScalar(1)

  switch (props.action) {
    case 'attack': {
      // 蹬地、拧腰、双手抡棒横扫。
      const windup = Math.sin(Math.min(1, phase * 1.45) * Math.PI)
      rig.root.position.x = pulse * 0.58
      rig.root.position.y += pulse * 0.08
      rig.root.rotation.y = -0.12 - pulse * 0.52
      s.pelvis.rotation.y = -pulse * 0.25
      s.spine.rotation.y = -pulse * 0.34
      s.chest.rotation.y = -pulse * 0.42
      s.rightUpperArm.rotation.z = 0.28 - windup * 1.72
      s.rightForearm.rotation.z = 0.38 - windup * 0.72
      s.leftUpperArm.rotation.z = -0.34 + windup * 1.08
      s.leftForearm.rotation.z = -0.46 + windup * 0.82
      s.staff.rotation.z = 0.92 - windup * 1.18
      s.leftThigh.rotation.x = pulse * 0.28
      s.rightThigh.rotation.x = -pulse * 0.2
      s.cape.rotation.x = -0.08 + pulse * 0.52
      s.scarf.rotation.y = pulse * 0.68
      break
    }
    case 'defense':
      // 下沉重心，以金箍棒和双臂封在身前。
      rig.root.position.y -= pulse * 0.08
      rig.root.position.z = -pulse * 0.18
      s.pelvis.rotation.x = pulse * 0.08
      s.spine.rotation.x = pulse * 0.12
      s.leftUpperArm.rotation.z = -0.34 + pulse * 1.18
      s.leftForearm.rotation.z = -0.46 + pulse * 0.82
      s.rightUpperArm.rotation.z = 0.28 - pulse * 1.18
      s.rightForearm.rotation.z = 0.38 - pulse * 0.82
      s.staff.rotation.z = 0.92 - pulse * 0.9
      s.leftThigh.rotation.x = -pulse * 0.16
      s.rightThigh.rotation.x = -pulse * 0.16
      rig.aura.material.opacity = 0.24 + pulse * 0.56
      rig.aura.scale.setScalar(1 + pulse * 0.28)
      break
    case 'ability':
    case 'power':
      // 腾空施法，冠羽、披风、尾巴和光环同步展开。
      rig.root.position.y += pulse * 0.28
      rig.root.rotation.y += phase * Math.PI * 2
      s.spine.rotation.x = -pulse * 0.12
      s.chest.rotation.x = -pulse * 0.18
      s.head.rotation.x = -pulse * 0.12
      s.leftUpperArm.rotation.z = -0.34 - pulse * 1.05
      s.leftForearm.rotation.z = -0.46 + pulse * 0.3
      s.rightUpperArm.rotation.z = 0.28 + pulse * 1.05
      s.rightForearm.rotation.z = 0.38 - pulse * 0.3
      s.staff.rotation.z = 0.92 + pulse * 0.42
      s.leftThigh.rotation.x = pulse * 0.18
      s.rightThigh.rotation.x = -pulse * 0.18
      s.cape.rotation.x = -0.08 + pulse * 0.72
      s.scarf.rotation.y = pulse * 1.1
      s.tail.forEach((bone, index) => { bone.rotation.z += pulse * (0.28 + index * 0.1) })
      rig.aura.material.opacity = 0.25 + pulse * 0.68
      rig.aura.scale.setScalar(1 + pulse * 0.55)
      break
    case 'hit':
      rig.root.position.x = Math.sin(phase * Math.PI * 8) * (1 - phase) * 0.23
      rig.root.rotation.z = -pulse * 0.22
      s.pelvis.rotation.x = -pulse * 0.14
      s.spine.rotation.x = -pulse * 0.3
      s.chest.rotation.x = -pulse * 0.22
      s.head.rotation.x = pulse * 0.28
      s.leftUpperArm.rotation.z -= pulse * 0.32
      s.rightUpperArm.rotation.z += pulse * 0.32
      s.cape.rotation.x = -0.08 + pulse * 0.65
      break
  }

  rig.skeleton?.update()
}

function animateGenericRig(now: number, phase: number, pulse: number) {
  if (!rig) return
  const idle = now / 1000

  rig.root.position.set(0, Math.sin(idle * 2.1) * 0.035, 0)
  rig.root.rotation.set(0, -0.16 + Math.sin(idle * 0.8) * 0.05, 0)
  rig.torso.rotation.set(0, 0, 0)
  rig.head.rotation.set(0, Math.sin(idle * 0.7) * 0.05, 0)
  rig.leftArm.rotation.set(0, 0, -0.2 + Math.sin(idle * 2.1) * 0.025)
  rig.rightArm.rotation.set(0, 0, 0.2 - Math.sin(idle * 2.1) * 0.025)
  rig.leftLeg.rotation.x = 0
  rig.rightLeg.rotation.x = 0
  rig.aura.material.opacity = 0.18 + Math.sin(idle * 2.2) * 0.04
  rig.aura.scale.setScalar(1)

  switch (props.action) {
    case 'attack':
      rig.root.position.x = pulse * 0.62
      rig.root.rotation.y = -0.16 - pulse * 0.42
      rig.rightArm.rotation.z = 0.2 - pulse * 2.15
      rig.leftArm.rotation.z = -0.2 + pulse * 0.65
      break
    case 'defense':
      rig.root.position.z = -pulse * 0.25
      rig.torso.rotation.x = pulse * 0.16
      rig.leftArm.rotation.z = -0.2 + pulse * 1.35
      rig.rightArm.rotation.z = 0.2 - pulse * 1.35
      rig.aura.material.opacity = 0.26 + pulse * 0.45
      rig.aura.scale.setScalar(1 + pulse * 0.25)
      break
    case 'ability':
    case 'power':
      rig.root.position.y += pulse * 0.22
      rig.root.rotation.y += phase * Math.PI * 2
      rig.leftArm.rotation.z = -0.2 - pulse * 1.08
      rig.rightArm.rotation.z = 0.2 + pulse * 1.08
      rig.aura.material.opacity = 0.25 + pulse * 0.62
      rig.aura.scale.setScalar(1 + pulse * 0.5)
      break
    case 'hit':
      rig.root.position.x = Math.sin(phase * Math.PI * 8) * (1 - phase) * 0.2
      rig.root.rotation.z = -pulse * 0.24
      rig.torso.rotation.x = -pulse * 0.25
      break
  }
}

function animate(now = performance.now()) {
  if (!renderer || !scene || !camera || !rig) return
  const elapsed = (now - startedAt) / 1000
  const phase = Math.min(1, elapsed / 0.86)
  const pulse = Math.sin(phase * Math.PI)
  if (rig.sun) animateSunRig(now, phase, pulse)
  else animateGenericRig(now, phase, pulse)

  rig.aura.rotation.z += 0.008
  renderer.render(scene, camera)
  frameId = requestAnimationFrame(animate)
}

watch(() => [props.action, resolvedToken.value], () => { startedAt = performance.now() })
watch(() => props.characterClass, rebuildRig)

onMounted(() => nextTick(setupScene))
onBeforeUnmount(() => {
  cancelAnimationFrame(frameId)
  resizeObserver?.disconnect()
  if (rig && scene) {
    scene.remove(rig.root)
    disposeObject(rig.root)
  }
  renderer?.dispose()
  renderer?.domElement.remove()
})
</script>

<style scoped>
.character-3d {
  --viewport-width: 190px;
  --viewport-height: 285px;
  position: relative;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  width: var(--viewport-width);
  filter: drop-shadow(0 18px 16px rgba(0, 0, 0, 0.45));
}
.character-3d--sm { --viewport-width: 118px; --viewport-height: 172px; }
.character-3d--lg { --viewport-width: 230px; --viewport-height: 330px; }
.character-3d__viewport { width: var(--viewport-width); height: var(--viewport-height); }
.character-3d__viewport :deep(canvas) { display: block; width: 100%; height: 100%; }
.character-3d__plate {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 82%;
  min-height: 26px;
  margin-top: -22px;
  border: 1px solid rgba(242, 169, 0, 0.42);
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(15, 14, 23, 0.82), rgba(52, 39, 30, 0.88), rgba(15, 14, 23, 0.82));
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 13px;
  letter-spacing: 2px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.42);
}
.character-3d__sigil { font-size: 16px; }
.character-3d__action {
  position: absolute;
  top: 12%;
  right: -12px;
  padding: 4px 9px;
  border: 1px solid currentColor;
  border-radius: 999px;
  background: rgba(15, 14, 23, 0.82);
  color: var(--gold);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
  box-shadow: 0 0 15px currentColor;
}
.character-3d--attack .character-3d__action { color: var(--red); }
.character-3d--defense .character-3d__action { color: var(--blue); }
.character-3d--ability .character-3d__action,
.character-3d--power .character-3d__action { color: var(--purple); }
.character-3d--hit .character-3d__action { color: #ff8f8f; }

@media (prefers-reduced-motion: reduce) {
  .character-3d__action { box-shadow: none; }
}
</style>
