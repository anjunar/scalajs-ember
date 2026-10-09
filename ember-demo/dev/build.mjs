import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const full = process.argv.includes('--full')
const task = `scalajs-ember-demo/${full ? 'fullLinkJS' : 'fastLinkJS'}`
const windows = process.platform === 'win32'
const command = windows ? process.env.ComSpec ?? 'cmd.exe' : 'sbt'
const args = windows ? ['/d', '/c', 'sbt', '--server', task] : ['--server', task]
const build = spawn(command, args, {
  cwd: fileURLToPath(new URL('../../', import.meta.url)),
  stdio: 'inherit',
})
build.on('error', error => { console.error(error.message); process.exitCode = 1 })
build.on('exit', code => { process.exitCode = code ?? 1 })
