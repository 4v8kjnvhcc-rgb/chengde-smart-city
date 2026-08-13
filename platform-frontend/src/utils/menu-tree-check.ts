/** 角色菜单勾选：回显时只设叶子，避免把「有下级的目录」id 传入 setCheckedKeys 导致整枝全选 */

export interface MenuCheckRow {
  id: number
  parentId?: number | null
  menuType?: number | null
}

/**
 * 回显勾选 key：
 * - 有下级的节点不回显（由子项级联勾上）
 * - 无下级的节点（含空目录，如平台管理 / 业务功能平台）可回显，否则「勾了保存再开又没了」
 */
export function leafKeysForTreeCheck(rows: MenuCheckRow[], assigned: number[]): number[] {
  const byId = new Map(rows.map((r) => [r.id, r]))
  const parentsWithChildren = new Set(
    rows.map((r) => r.parentId).filter((p): p is number => p != null && p !== 0),
  )
  return (assigned || []).filter((id) => {
    const row = byId.get(id)
    if (!row) return false
    if (parentsWithChildren.has(id)) return false
    return true
  })
}
