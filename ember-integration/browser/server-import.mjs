// Nachweis: das Modul laesst sich im Serverprozess laden, ohne Browserglobals zu beruehren.
//
// §15.2 haelt das ausdruecklich fest: "Browserzugriffe werden erst beim Attach ausgefuehrt;
// Importieren eines Moduls im Serverprozess darf nicht bereits `window` oder `document` lesen."
// Das ist die Voraussetzung fuer SSR -- ohne sie waere jede serverseitige Verwendung eine
// Frage der Ladereihenfolge.
//
// Node bringt weder `window` noch `document` mit. Ein Zugriff beim Laden wuerde also nicht
// stillschweigend gutgehen, sondern hier werfen -- genau das soll dieser Lauf zeigen.

import { readFile } from 'node:fs/promises'

const output = new URL('../../target/ember-browser-tests/main.js', import.meta.url)

await readFile(output).catch(() => {
  console.error(
    'Kein Linkeroutput. Zuerst:\n' +
      '  sbt --server "scalajs-ember-integration/fullLinkJS"'
  )
  process.exit(1)
})

if (typeof globalThis.window !== 'undefined') {
  console.error('Dieser Lauf braucht eine Umgebung ohne window.')
  process.exit(1)
}

const module = await import(output.href)

if (typeof module.emberFixtures !== 'object') {
  console.error('Der Export `emberFixtures` fehlt im gelinkten Modul.')
  process.exit(1)
}

// Gegenprobe: der Import darf die Globals auch nicht angelegt haben.
for (const name of ['window', 'document']) {
  if (typeof globalThis[name] !== 'undefined') {
    console.error(`Der Import hat \`${name}\` angelegt.`)
    process.exit(1)
  }
}

console.log('Serverimport ohne Browserglobals: in Ordnung.')
