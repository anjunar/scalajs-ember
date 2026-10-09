import { readFile, readdir, mkdir, writeFile } from 'node:fs/promises'
import { resolve, relative, isAbsolute } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const directory = resolve(root, 'target/editor-metadata')
const files = (await readdir(directory)).filter(name => name.endsWith('.json'))
if (!files.length) throw new Error('Missing sbt metadata. Run sbt --server editorMetadata first.')
const projects = await Promise.all(files.map(async file => JSON.parse(await readFile(resolve(directory, file), 'utf8'))))
const byId = new Map(projects.map(project => [project.id, project]))
const errors = []
const prefix = (value, name) => value === name || value.startsWith(`${name}.`)
const production = edge => edge.configuration.split(';').some(part => /^(compile|\*)(,|->|$)/.test(part))
for (const project of projects) {
  for (const edge of project.dependencies) {
    if (!byId.has(edge.id)) errors.push(`${project.id}: missing metadata for ${edge.id}`)
    if (!project.publishSkip && !project.allowedProjects.includes(edge.id)) errors.push(`${project.id}: forbidden project ${edge.id}`)
    if (!project.publishSkip && production(edge) && byId.get(edge.id)?.publishSkip) errors.push(`${project.id}: depends on unpublished ${edge.id}`)
  }
  for (const module of project.compileModules) {
    const name = module.split(':')[1]
    if (project.forbiddenModules.some(banned => name.includes(banned)) && !project.allowedModules.some(allowed => name === `${allowed}_sjs1_3`)) {
      errors.push(`${project.id}: forbidden compile module ${module}`)
    }
  }
  for (const source of project.sources) {
    const local = relative(root, source)
    if (isAbsolute(local) || local.startsWith('..')) throw new Error(`Unexpected external source: ${source}`)
    const lines = (await readFile(source, 'utf8')).split(/\r?\n/)
    for (const [index, line] of lines.entries()) {
      const match = /^\s*import\s+(.+)/.exec(line)
      if (match && project.forbiddenImports.some(name => prefix(match[1], name))) errors.push(`${local}:${index + 1}: ${match[1]}`)
    }
  }
}
const visited = new Set(), visiting = new Set()
function walk(id, path = []) {
  if (visiting.has(id)) { errors.push(`Dependency cycle: ${[...path, id].join(' -> ')}`); return }
  if (visited.has(id)) return
  visiting.add(id)
  for (const edge of byId.get(id)?.dependencies ?? []) if (production(edge)) walk(edge.id, [...path, id])
  visiting.delete(id); visited.add(id)
}
for (const id of byId.keys()) walk(id)
await mkdir(resolve(root, 'target/p28'), { recursive: true })
await writeFile(resolve(root, 'target/p28/boundaries.json'), JSON.stringify({ projects: projects.length, errors,
  graph: projects.map(p => ({ id: p.id, publishSkip: p.publishSkip, dependencies: p.dependencies, compileModules: p.compileModules })) }, null, 2))
if (errors.length) throw new Error(errors.join('\n'))
console.log(`${projects.length} projects: no cycles, forbidden compile dependencies or source imports; publish graph valid.`)
