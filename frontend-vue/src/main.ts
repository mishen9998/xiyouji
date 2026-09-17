// ====== Vue 3 应用入口 ======
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/global.css'
import './styles/battle-theme.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')

// CSS animation work also stops when the tab is hidden.
const updateVisibility = () => { document.documentElement.dataset.pageHidden = String(document.hidden) }
document.addEventListener('visibilitychange', updateVisibility)
updateVisibility()
