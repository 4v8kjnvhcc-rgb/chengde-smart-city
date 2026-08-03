/** 角色菜单勾选：回显时只设叶子，避免把目录 id 传入 setCheckedKeys 导致整枝全选 */

export interface MenuCheckRow {
  id: number
  parentId?: number | null
  menuType?: number | null
}

export function leafKeysForTreeCheck(rows: MenuCheckRow[], assigned: number[]): number[] {
  const byId = new Map(rows.map((r) => [r.id, r]))
  const parentsWithChildren = new Set(
    rows.map((r) => r.parentId).filter((p): p is number => p != null && p !== 0),
  )
  return (assigned || []).filter((id) => {
    const row = byId.get(id)
    if (!row) return false
    if (row.menuType === 1) return false
    if (parentsWithChildren.has(id)) return false
    return true
  })
}
