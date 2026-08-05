import { computed, ref, type ComputedRef, type Ref } from 'vue'

type ListSource<T> = Ref<T[]> | ComputedRef<T[]> | (() => T[])

/**
 * 治理平台列表前端切片分页（配合 PortalPagination 中文文案）
 */
export function useClientPager<T>(source: ListSource<T>, defaultPageSize = 10) {
  const page = ref(1)
  const pageSize = ref(defaultPageSize)

  const list = computed(() => {
    if (typeof source === 'function') return source() || []
    return source.value || []
  })

  const total = computed(() => list.value.length)

  const paged = computed(() => {
    const start = (page.value - 1) * pageSize.value
    return list.value.slice(start, start + pageSize.value)
  })

  function resetPage() {
    page.value = 1
  }

  return { page, pageSize, paged, total, resetPage, list }
}
