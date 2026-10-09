# Ember browser harness — npm package

The Playwright/Node side of [`ember-integration`](../README.md): the test runner, the SSR-check
server, and small standalone servers for the toolbar and media-upload demos used by the browser
suites. See the parent module's README for what each suite proves and how to run the full harness.

## Scripts

```bash
npm run test:server   # loads the linked module in a plain Node process (no window/document)
npm run test:browser  # runs the Playwright suites in Chromium, Firefox and WebKit
npm run test:bench    # performance suites (single worker)
npm run test:bench:stress  # large-document stress suite (50,000-sibling reorder)
npm run verify         # test:server + test:browser
```

All of these require a prior `sbt --server "scalajs-ember-integration/fullLinkJS"` from the
repository root — see [../README.md](../README.md) for why it must be a full, not a fast, link.

## Standalone demo servers

`server.mjs` and `media-server.mjs` serve the toolbar and media-upload demo pages the browser
suites drive (`http://127.0.0.1:4188/toolbar`, and the endpoints `multipart.spec.mjs` /
`media.spec.mjs` exercise). Both are intentionally minimal fixture servers — a bounded upload
target, not a production backend.

`acceptance-trace.mjs` reads the exported event/build trace produced by `/toolbar?trace=1`, an
opt-in, 2000-event-capped recording tool used to attach reproducible evidence to a manual device
acceptance pass. Automated input exercises the tooling itself, not a real input method — a real
IME/screen-reader/touch acceptance stays a manual, documented pass.

## Related

- [`../README.md`](../README.md) — the sbt module, suite list, and Firefox-on-Windows workaround.
- [`../../ember-toolbar/README.md`](../../ember-toolbar/README.md) — the toolbar this package's demo server mounts.
</content>
