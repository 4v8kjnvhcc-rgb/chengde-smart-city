import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 固定 3000。若 EACCES：管理员执行 net stop winnat 后
    // netsh int ipv4 add excludedportrange protocol=tcp startport=3000 numberofports=1 store=persistent
    // 再 net start winnat（避免 Hyper-V 动态预留吞掉 3000）
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
