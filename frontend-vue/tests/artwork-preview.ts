// Development-only fixture, excluded from the production entry. No account/save access.
import { createApp } from 'vue'
import Preview from './ArtworkPreview.vue'
import '@/styles/global.css'
import '@/styles/battle-theme.css'
createApp(Preview).mount('#app')
