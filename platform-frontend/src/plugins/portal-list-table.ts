import type { App, Component } from 'vue'
import { defineComponent, h } from 'vue'
import { ElTable, ElTableColumn } from 'element-plus'

type PropDef = { type?: unknown; default?: unknown; [k: string]: unknown }

/**
 * 列表表格全局约定：
 * - el-table 默认 border=true（可拖拽调列宽）
 * - 无 prop 且 label 为「操作」的按钮列，自动 fixed=right（业务「操作类型」字段应带 prop）
 */
function patchElTableDefaults() {
  const props = (ElTable as unknown as { props?: Record<string, PropDef> }).props
  if (!props?.border) return
  const prev = props.border
  props.border = {
    ...prev,
    type: Boolean,
    default: true,
  }
}

function shouldFixOpsColumn(attrs: Record<string, unknown>): boolean {
  if (attrs.label !== '操作') return false
  if (attrs.prop != null && String(attrs.prop) !== '') return false
  if (attrs.fixed != null && attrs.fixed !== false) return false
  const w = attrs.width
  if (w == null || w === '') return true
  const n = Number(w)
  return Number.isFinite(n) && n >= 120
}

function installElTableColumnWrap(app: App) {
  const Orig = ElTableColumn as Component
  const Wrapped = defineComponent({
    name: 'ElTableColumn',
    inheritAttrs: false,
    setup(_, { attrs, slots }) {
      return () => {
        const next: Record<string, unknown> = { ...attrs }
        if (shouldFixOpsColumn(attrs as Record<string, unknown>)) {
          next.fixed = 'right'
        }
        return h(Orig, next, slots)
      }
    },
  })
  app.component('ElTableColumn', Wrapped)
  app.component('el-table-column', Wrapped)
}

/** 须在 app.use(ElementPlus) 之后调用（覆盖列组件注册） */
export function installPortalListTable(app: App) {
  patchElTableDefaults()
  installElTableColumnWrap(app)
}

/** 可在 use(ElementPlus) 前调用，仅打默认 props */
export function preparePortalListTableDefaults() {
  patchElTableDefaults()
}
