import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const build = spawn(process.execPath, [fileURLToPath(new URL('build.mjs', import.meta.url))], { stdio: 'inherit' })
build.on('error', error => { console.error(error.message); process.exitCode = 1 })
build.on('exit', async code => {
  if (code !== 0) process.exitCode = code ?? 1
  else await import('./server.mjs')
})
