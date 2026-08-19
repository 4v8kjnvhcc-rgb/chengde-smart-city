import 'vue'

declare module 'vue' {
  interface ComponentCustomProperties {
    $statusLabel: (value: unknown, domain?: import('./utils/status-label').StatusLabelDomain) => string
    $statusTagType: (value: unknown) => 'success' | 'warning' | 'info' | 'danger' | 'primary'
  }
}

export {}
