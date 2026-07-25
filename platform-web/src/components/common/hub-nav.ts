export interface HubNavItem {
  key: string
  label: string
  subLabel?: string
  /** 子菜单项；可再嵌套（如 V3.0「数据融合处理」下的五子能力） */
  children?: HubNavItem[]
}

export interface HubNavGroup {
  title: string
  items: HubNavItem[]
}
