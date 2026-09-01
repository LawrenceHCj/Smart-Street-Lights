import { createApp } from 'vue'
import {
  ElButton,
  ElConfigProvider,
  ElDialog,
  ElDrawer,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElLoadingDirective,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTimeSelect,
  ElTooltip,
} from 'element-plus'
import '@fontsource-variable/inter'
import 'element-plus/dist/index.css'
import './styles/index.css'
import App from './App.vue'
import router from './router'

// Straightforward 品牌主题：浅色工作面与高对比度海军蓝结构层。
document.documentElement.classList.add('brand-theme')

const app = createApp(App)

const elementComponents = [
  ElButton,
  ElConfigProvider,
  ElDialog,
  ElDrawer,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTimeSelect,
  ElTooltip,
]

for (const component of elementComponents) {
  if (component.name) app.component(component.name, component)
}

app.directive('loading', ElLoadingDirective)
app.use(router).mount('#app')
