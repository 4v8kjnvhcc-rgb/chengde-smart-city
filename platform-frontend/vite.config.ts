import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import fs from 'node:fs'
import net from 'node:net'
import path from 'node:path'
import { execSync } from 'node:child_process'

// #region agent log
const DEBUG_LOG = path.resolve(fileURLToPath(new URL('.', import.meta.url)), '../debug-49920f.log')
function agentLog(hypothesisId: string, message: string, data: Record<string, unknown>) {
  try {
    fs.appendFileSync(
      DEBUG_LOG,
      JSON.stringify({
        sessionId: '49920f',
        runId: 'pre-fix',
        hypothesisId,
        location: 'vite.config.ts',
        message,
        data,
        timestamp: Date.now(),
      }) + '\n',
    )
  } catch {
    /* ignore */
  }
}
async function probeDevPort() {
  const port = 4000
  const inUse = await new Promise<boolean>((resolve) => {
    const s = net.createServer()
    s.once('error', () => resolve(true))
    s.once('listening', () => s.close(() => resolve(false)))
    s.listen(port, '127.0.0.1')
  })
  agentLog('A', 'port_4000_probe', { port, inUse, strictPort: true })
  let occupant: { pid?: string; cmd?: string } = {}
  try {
    const out = execSync('netstat -ano | findstr ":4000" | findstr LISTENING', {
      encoding: 'utf8',
      shell: 'cmd.exe',
    })
    const m = out.trim().split(/\s+/).pop()
    if (m) {
      occupant.pid = m
      try {
        const wmic = execSync(
          `powershell -NoProfile -Command "(Get-CimInstance Win32_Process -Filter \\"ProcessId=${m}\\").CommandLine"`,
          { encoding: 'utf8' },
        )
        occupant.cmd = wmic.trim()
      } catch {
        /* ignore */
      }
    }
  } catch {
    /* not listening or netstat failed */
  }
  const isThisProject = Boolean(occupant.cmd?.includes('chengde-smart-city\\platform-frontend'))
  const isOtherVite = Boolean(occupant.cmd?.toLowerCase().includes('vite'))
  agentLog('B', 'port_occupant', { ...occupant, isThisProject, isOtherVite })
  agentLog('C', 'strictPort_will_hard_fail', { strictPort: true, willFailIfInUse: inUse && true })
  agentLog('D', 'same_repo_zombie', { isThisProject, inUse })
}
void probeDevPort()
// #endregion

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 固定 4000。若 EACCES：管理员执行 net stop winnat 后
    // netsh int ipv4 add excludedportrange protocol=tcp startport=4000 numberofports=1 store=persistent
    // 再 net start winnat（避免 Hyper-V 动态预留吞掉 4000）
    port: 4000,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true,
      },
    },
  },
})
