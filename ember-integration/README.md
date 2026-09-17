# scalajs-ember-integration

The browser test harness for the Ember editor. **Not published** — it exists to run the actually
linked application in real browser engines, not to ship as a library.

| | |
| --- | --- |
| sbt ID | `scalajs-ember-integration` |
| Scala package | `ember.editor.integration` |
| Dependencies | every library module: `core`, `rich-text`, `list`, `link`, `code`, `table`, `code-highlighting`, `image`, `history`, `markdown`, `json`, `html`, `ui`, `browser`, `browser-support`, `clipboard`, `forms`, `toolbar`, `standard` |

## Why it exists

Many unit-level tests use a DOM stub, and stubs don't provide a trustworthy IME/selection engine.
This module runs the **actually linked** Scala editor in Chromium, Firefox and WebKit, so a green
run here means the Ember engine and the UI runtime work together in the same bundle, real
keystrokes reach the model, and disposal cleans up both.

## Running it

```bash
sbt --server "scalajs-ember-integration/fullLinkJS"
cd ember-integration/browser
npm ci
npx playwright install chromium firefox webkit
npm run verify
```

`npm run verify` runs in two stages:

| Step | What it proves |
| --- | --- |
| `test:server` | The module loads in a server process without touching `window` or `document` — a prerequisite for SSR. |
| `test:browser` | The full chain, in three real engines. |

Both abort loudly without a prior link, rather than running against a stale build. **The link must
be `fullLinkJS`.** The server reads from `target/ember-browser-tests/`; `fastLinkJS` writes to
`target/ember-browser-tests-fast/`, which the harness never looks at, and it fails loudly when
**no** output exists — a stale one looks identical to a current one.

### Firefox on Windows

The Firefox build bundled with Playwright fails to launch on some Windows machines
(`browserType.launch: spawn UNKNOWN`), due to a missing private side-by-side assembly
(`mozglue`) that Windows refuses to resolve even though the file and its manifest are present. A
regularly installed Firefox of the same version starts fine, so the problem is specific to the
bundled build, not the project or the machine. The workaround is to point Playwright at the
installed Firefox instead, over WebDriver BiDi rather than Juggler:

```bash
EMBER_FIREFOX_CHANNEL=moz-firefox npm run test:browser
```

CI runs under Linux against the bundled build, which stays the canonical target browser.

## Suites

| File | Checks |
| --- | --- |
| `identity.spec.mjs` | The full chain: DOM event → command → transaction → commit → projection, plus disposal and the error sink. |
| `text-splice.spec.mjs` | `spliceText` from `ui-core`: UTF-16 offsets, DOM text-node identity, and that an unchanged value triggers **no** write. |
| `move.spec.mjs` | `Runtime.move`: element and list identity, synchronized logical/DOM child lists, rejected operations without side effects. |
| `projection.spec.mjs` | The keyed `DocumentView`: DOM identity across text edits and moves, the scope of writes, and that SSR and the browser produce the same initial output. |
| `editor-hydration.spec.mjs` | Claiming a server-rendered page, including content typed before the script ran. |
| `selection.spec.mjs` / `focus.spec.mjs` | The position mapping table in both directions; focus scope across iframes and Shadow DOM. |
| `editing.spec.mjs` / `native-input.spec.mjs` | Real key events through `beforeinput`/`input`, including engine-specific quirks. |
| `composition.spec.mjs` / `mutation-race.spec.mjs` | IME composition protocol; recovery from mutations the editor didn't cause. |
| `clipboard.spec.mjs` / `drop.spec.mjs` | Copy/cut/paste and drag/drop, including MIME fallback and confirmed cut. |
| `media.spec.mjs` / `multipart.spec.mjs` | The upload pipeline, including a no-JavaScript multipart submission. |
| `table-editing.spec.mjs` | Table typing, structural commands, Tab/Escape, drag-to-select cells. |
| `code-highlighting.spec.mjs` | Syntax highlighting as a pure view decoration. |
| `toolbar-a11y.spec.mjs` | Toolbar/dialog accessibility: mouse, keyboard, focus management, Forced Colors, Reduced Motion. |
| `nojs-form.spec.mjs` / `source-form.spec.mjs` | The form field with JavaScript disabled, and in source mode. |
| `profiles.spec.mjs` | Three independently linked profile applications (text, markdown, standard) under `/profile?name=...`. |

`text-splice` and `move` deliberately do not duplicate the neighbor UI project's own suite — that
suite covers the same contracts JVM-side; what it cannot cover is **DOM node identity**, an `===`
comparison on real DOM objects that a newly created text node would fail, taking the caret,
selection and any running IME input down with it. The no-op contract ("identical text produces no
mutation") is checked with a `MutationObserver` — the only honest witness, since it also sees a
write that sets the same value.

## What a browser test cannot answer either

A real input method. Real device/IME/screen-reader acceptance requires a manual test with a
documented device result and is outside what any automated suite — synthetic or real-keyboard —
can certify.

## Related modules

- [`ember-browser`](../ember-browser/README.md), [`ember-browser-support`](../ember-browser-support/README.md) — the input pipeline exercised here.
- [`ember-standard`](../ember-standard/README.md), [`ember-toolbar`](../ember-toolbar/README.md), [`ember-forms`](../ember-forms/README.md) — the feature set assembled into this harness.
- [`ember-demo`](../ember-demo/README.md) — the separate, user-facing showcase application (not a test harness).
</content>
