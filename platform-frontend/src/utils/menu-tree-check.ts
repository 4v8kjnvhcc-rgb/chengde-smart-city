/** 角色菜单勾选：回显/保存都只保留叶子（及无下级的空目录），避免「有下级的目录」入库后把 Hub 侧栏整枝裁掉 */

export interface MenuCheckRow {
  id: number
  parentId?: number | null
  menuType?: number | null
}

/**
 * 回显/持久化用的菜单 id：
 * - 有下级的节点排除（由子项级联勾上；全选目录时 Element 也会把父 id 放进 getCheckedKeys）
 * - 无下级的节点（含空目录，如平台管理 / 业务功能平台）保留
 */
export function leafKeysForTreeCheck(rows: MenuCheckRow[], assigned: number[]): number[] {
  const byId = new Map(rows.map((r) => [Number(r.id), r]))
  const parentsWithChildren = new Set(
    rows
      .map((r) => (r.parentId == null ? 0 : Number(r.parentId)))
      .filter((p) => Number.isFinite(p) && p !== 0),
  )
  return [...new Set((assigned || []).map((id) => Number(id)).filter((id) => Number.isFinite(id)))].filter(
    (id) => {
      const row = byId.get(id)
      if (!row) return false
      if (parentsWithChildren.has(id)) return false
      return true
    },
  )
}
