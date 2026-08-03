import { ingestionApi, type DataSource, type DataTable, type Dict } from './useIngestionHub'

type CacheSlot<T> = { data: T | null; promise: Promise<T> | null }

const slots = {
  dataSources: { data: null, promise: null } as CacheSlot<DataSource[]>,
  tables: { data: null, promise: null } as CacheSlot<DataTable[]>,
  dicts: { data: null, promise: null } as CacheSlot<Dict[]>,
}

async function loadSlot<T>(
  slot: CacheSlot<T>,
  fetcher: () => Promise<T>,
  force = false,
): Promise<T> {
  if (!force && slot.data) return slot.data
  if (!force && slot.promise) return slot.promise
  slot.promise = fetcher().then((data) => {
    slot.data = data
    return data
  }).finally(() => {
    slot.promise = null
  })
  return slot.promise
}

export const ingestionRegisterCache = {
  dataSources: (force = false) =>
    loadSlot(slots.dataSources, () => ingestionApi.dataSources().then((r) => r.data), force),

  tables: (force = false) =>
    loadSlot(slots.tables, () => ingestionApi.tables().then((r) => r.data), force),

  dicts: (force = false) =>
    loadSlot(slots.dicts, () => ingestionApi.dicts().then((r) => r.data), force),

  invalidate(...keys: Array<keyof typeof slots>) {
    const targets = keys.length ? keys : (Object.keys(slots) as Array<keyof typeof slots>)
    for (const key of targets) {
      slots[key].data = null
      slots[key].promise = null
    }
  },
}
