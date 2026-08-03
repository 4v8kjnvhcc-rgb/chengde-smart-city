import { computed, ref, watch } from 'vue'
import type { Project } from './useIngestionHub'

const STORAGE_KEY = 'ingestion.activeProjectId'

function readStored(): number | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const n = Number(raw)
    return Number.isFinite(n) && n > 0 ? n : null
  } catch {
    return null
  }
}

/** 归集登记各子模块共享的当前项目（会话级） */
export const activeProjectId = ref<number | null>(readStored())

watch(activeProjectId, (id) => {
  try {
    if (id == null) sessionStorage.removeItem(STORAGE_KEY)
    else sessionStorage.setItem(STORAGE_KEY, String(id))
  } catch {
    /* ignore */
  }
})

export function setActiveProjectId(id: number | null) {
  activeProjectId.value = id
}

/** 从项目列表同步：无选中则取第一个；选中已失效则纠正 */
export function syncActiveProject(projects: Project[]) {
  if (!projects.length) {
    activeProjectId.value = null
    return
  }
  if (activeProjectId.value && projects.some((p) => p.id === activeProjectId.value)) return
  activeProjectId.value = projects[0].id
}

export function projectOptionLabel(p: Project) {
  const sys = (p.systemName || '').trim()
  return sys ? `${p.projectName} / ${sys}` : p.projectName
}

export function useActiveProject(projects: { value: Project[] }) {
  const activeProject = computed(() =>
    projects.value.find((p) => p.id === activeProjectId.value) || null,
  )
  return { activeProjectId, activeProject, setActiveProjectId, syncActiveProject, projectOptionLabel }
}
