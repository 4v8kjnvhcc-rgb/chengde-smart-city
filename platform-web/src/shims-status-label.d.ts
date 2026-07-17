import 'vue'

declare module 'vue' {
  interface ComponentCustomProperties {
    $statusLabel: (value: unknown) => string
    $statusTagType: (value: unknown) => 'success' | 'warning' | 'info' | 'danger' | 'primary'
  }
}

export {}
