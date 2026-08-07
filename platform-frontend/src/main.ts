import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { statusLabel, statusTagType } from './utils/status-label'
import { formatDateTime } from './utils/datetime'
import './styles/variables.css'
import './styles/global.css'
import './styles/element-overrides.css'
import './style.css'

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.config.globalProperties.$statusLabel = statusLabel
app.config.globalProperties.$statusTagType = statusTagType
app.config.globalProperties.$formatDateTime = formatDateTime
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
