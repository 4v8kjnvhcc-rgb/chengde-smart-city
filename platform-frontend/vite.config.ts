import { defineConfig, type Plugin } from 'vite'
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
  const port = 9087
  const inUse = await new Promise<boolean>((resolve) => {
    const s = net.createServer()
    s.once('error', () => resolve(true))
    s.once('listening', () => s.close(() => resolve(false)))
    s.listen(port, '127.0.0.1')
  })
  agentLog('A', 'port_9087_probe', { port, inUse, strictPort: true })
  let occupant: { pid?: string; cmd?: string } = {}
  try {
    const out = execSync('netstat -ano | findstr ":9087" | findstr LISTENING', {
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

const APP_BASE = process.env.VITE_BASE || '/bigdata-web/'

/** 允许访问 /bigdata-web（无尾斜杠）与 / ，自动跳到 /bigdata-web/ */
function redirectAppBasePlugin(): Plugin {
  const baseNoSlash = APP_BASE.replace(/\/$/, '') || '/bigdata-web'
  const baseWithSlash = baseNoSlash + '/'
  return {
    name: 'redirect-app-base',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const raw = req.url || '/'
        const pathOnly = raw.split('?')[0]
        const qs = raw.includes('?') ? raw.slice(raw.indexOf('?')) : ''
        if (pathOnly === baseNoSlash || pathOnly === '/') {
          res.statusCode = 302
          res.setHeader('Location', baseWithSlash + qs)
          res.end()
          return
        }
        next()
      })
    },
  }
}

export default defineConfig({
  base: APP_BASE,
  plugins: [vue(), redirectAppBasePlugin()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 本地入口：http://127.0.0.1:9087/bigdata-web （无斜杠会自动跳到 /bigdata-web/）
    port: 9087,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true,
      },
    },
  },
})
