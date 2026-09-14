# P28: gemessene Werte

Erzeugt aus den JSON-Belegen am 2026-09-14T10:07:25.335Z. Methodik und offene
Abnahmegrenzen: [Messbericht](../report.md). Daten: [acceptance.json](acceptance.json).

Umgebung: win32 10.0.26200; Intel(R) Core(TM) i7-14700KF; 28 logische CPUs;
32532.29 MiB RAM; Node v26.4.0; Basis ab06a60f3b0f67f9fa9cfb52d1940f16c319eab5;
Arbeitsbaum geändert: true. Full-Link, ES2021, ESModule.

## Core, History und Formstring

Commitzeiten in ms, inklusive der jeweiligen synchronen Reducer und Listener.
Die separate Vollserialisierung wird fünfmal gemessen; ihre p95 ist damit das Maximum.

| Form / Nodes | Modus | Commit p50 | Commit p95 | Serialisierung p50 | Serialisierung p95 | Undo-Stufen | geschätzte History-Bytes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| paragraphs / 1001 | core | 0.014 | 0.035 | 3.592 | 10.679 | 0 | 0 |
| paragraphs / 1001 | history | 0.033 | 0.057 | 5.542 | 7.394 | 32 | 6400 |
| paragraphs / 1001 | form | 2.115 | 2.525 | 1.859 | 1.996 | 0 | 0 |
| paragraphs / 10001 | core | 0.018 | 0.030 | 27.148 | 42.151 | 0 | 0 |
| paragraphs / 10001 | history | 0.028 | 0.041 | 28.868 | 38.975 | 32 | 6400 |
| paragraphs / 10001 | form | 20.118 | 23.373 | 18.551 | 19.749 | 0 | 0 |
| paragraphs / 100001 | core | 0.055 | 0.083 | 669.154 | 723.345 | 0 | 0 |
| paragraphs / 100001 | history | 0.079 | 0.215 | 524.427 | 760.302 | 32 | 6400 |
| paragraphs / 100001 | form | 535.223 | 612.157 | 608.894 | 618.527 | 0 | 0 |
| long-leaf / 3 | core | 0.220 | 0.524 | 77.903 | 122.737 | 0 | 0 |
| long-leaf / 3 | history | 0.293 | 0.876 | 79.910 | 91.356 | 2 | 8000256 |
| long-leaf / 3 | form | 81.631 | 104.313 | 91.183 | 109.675 | 0 | 0 |
| deep-list / 129 | core | 0.172 | 0.402 | 2.056 | 3.653 | 0 | 0 |
| deep-list / 129 | history | 0.190 | 0.478 | 1.419 | 2.448 | 32 | 6400 |
| deep-list / 129 | form | 1.175 | 1.410 | 0.780 | 0.907 | 0 | 0 |

## Node-Heap nach Freigabe

Einmalige Prozessmessung pro Fall, keine p50/p95 und kein Beweis für vollständige
Leakfreiheit. Modul-/JIT-Caches bleiben im Prozess; vor und nach dem Fall wird GC angefordert.

| Form / Nodes | Modus | vorher MiB | vor GC MiB | nach Dispose + GC MiB |
| --- | --- | --- | --- | --- |
| paragraphs / 1001 | core | 14.43 | 24.03 | 17.18 |
| paragraphs / 1001 | history | 16.31 | 23.09 | 16.89 |
| paragraphs / 1001 | form | 16.50 | 21.46 | 17.10 |
| paragraphs / 10001 | core | 16.63 | 37.47 | 17.31 |
| paragraphs / 10001 | history | 16.68 | 41.67 | 17.45 |
| paragraphs / 10001 | form | 16.75 | 56.72 | 17.46 |
| paragraphs / 100001 | core | 16.77 | 212.90 | 17.75 |
| paragraphs / 100001 | history | 16.92 | 205.30 | 17.79 |
| paragraphs / 100001 | form | 16.89 | 192.05 | 14.92 |
| long-leaf / 3 | core | 14.02 | 106.33 | 14.23 |
| long-leaf / 3 | history | 14.04 | 155.86 | 14.23 |
| long-leaf / 3 | form | 14.05 | 72.45 | 14.14 |
| deep-list / 129 | core | 14.05 | 23.36 | 14.52 |
| deep-list / 129 | history | 14.18 | 30.19 | 14.53 |
| deep-list / 129 | form | 14.20 | 21.74 | 14.54 |

## Browserprojektion

Je 50 Warmups und 1000 gemessene Änderungen, ein Worker. Commit enthält die synchrone
Projektion; Projected misst vom Start desselben Commits bis zum Callback, keinen Paint.
Mount, Move und Dispose sind Einzelmessungen in ms. „—“ bei Move bedeutet nicht gemessen:
100001 Nodes haben einen separaten roten Stressfall; Leaf und Liste keinen Wurzel-Block-Move.
0.000 ms liegt unter der jeweiligen Uhr-Auflösung, besonders bei Firefox.

| Engine / Version | Form / Nodes | Mount | Commit p50 / p95 | Projected p50 / p95 | Move | Dispose |
| --- | --- | --- | --- | --- | --- | --- |
| chromium 153.0.8010.12 | paragraphs / 1001 | 14.400 | 0.000 / 0.100 | 0.000 / 0.100 | 78.900 | 3.000 |
| chromium 153.0.8010.12 | paragraphs / 10001 | 64.200 | 0.000 / 0.100 | 0.000 / 0.100 | 14062.400 | 28.000 |
| chromium 153.0.8010.12 | paragraphs / 100001 | 512.300 | 0.000 / 0.100 | 0.000 / 0.100 | — | 91.100 |
| chromium 153.0.8010.12 | long-leaf / 3 | 5.500 | 0.500 / 0.700 | 0.500 / 0.700 | — | 0.400 |
| chromium 153.0.8010.12 | deep-list / 129 | 5.000 | 0.100 / 0.200 | 0.100 / 0.200 | — | 1.300 |
| firefox 155.0.1 | paragraphs / 1001 | 30.000 | 0.000 / 0.000 | 0.000 / 0.000 | 29.000 | 3.000 |
| firefox 155.0.1 | paragraphs / 10001 | 127.000 | 0.000 / 1.000 | 0.000 / 0.000 | 2585.000 | 47.000 |
| firefox 155.0.1 | paragraphs / 100001 | 3054.000 | 0.000 / 1.000 | 0.000 / 1.000 | — | 351.000 |
| firefox 155.0.1 | long-leaf / 3 | 16.000 | 3.000 / 10.000 | 3.000 / 10.000 | — | 1.000 |
| firefox 155.0.1 | deep-list / 129 | 16.000 | 0.000 / 1.000 | 0.000 / 1.000 | — | 2.000 |
| webkit 26.6 | paragraphs / 1001 | 18.000 | 0.000 / 0.000 | 0.000 / 0.000 | 36.000 | 4.000 |
| webkit 26.6 | paragraphs / 10001 | 75.000 | 0.000 / 0.000 | 0.000 / 0.000 | 1159.000 | 24.000 |
| webkit 26.6 | paragraphs / 100001 | 625.000 | 0.000 / 0.000 | 0.000 / 0.000 | — | 194.000 |
| webkit 26.6 | long-leaf / 3 | 12.000 | 0.000 / 1.000 | 0.000 / 1.000 | — | 1.000 |
| webkit 26.6 | deep-list / 129 | 9.000 | 0.000 / 1.000 | 0.000 / 1.000 | — | 1.000 |

Alle lokalen Fälle: ein geänderter Modellknoten, **0 Mounts/Unmounts**, 1050
Textmutationen einschließlich Warmup, **0 ChildList-/Attributmutationen**.
Die gemessenen Moves behalten die Host-Identität ohne Mount/Unmount.

## Chromium-Heap

CDP mit angefordertem GC. Firefox/WebKit bieten hier keine vergleichbare Heap-API;
ihre Werte bleiben null. Alle Browser prüfen zusätzlich einen leeren Host nach Dispose.

| Form / Nodes | vorher MiB | montiert nach GC MiB | nach Dispose + GC MiB |
| --- | --- | --- | --- |
| paragraphs / 1001 | 6.54 | 10.83 | 8.12 |
| paragraphs / 10001 | 6.54 | 32.69 | 8.18 |
| paragraphs / 100001 | 6.54 | 254.78 | 8.13 |
| long-leaf / 3 | 6.54 | 11.57 | 7.61 |
| deep-list / 129 | 6.54 | 8.45 | 7.94 |

## Profilgrößen

Tatsächliche JavaScript-Dateibytes, gzip Level 9 und Brotli-Default; keine Sourcemaps.
SHA-256 je Datei und ausgeführte Registrierungslisten stehen im JSON.

| Profil | JS Bytes | gzip Bytes | Brotli Bytes |
| --- | --- | --- | --- |
| text | 1988308 | 278695 | 196827 |
| markdown | 2548672 | 356777 | 252577 |
| standard | 2921675 | 403674 | 281840 |

## Automatisierte Gates

1266 bestandene Scala-Tests; 2.0.8 (Oracle Corporation Java 25.0.4.1).
802 tatsächliche Browserpässe, 2 erwartete Fehler,
0 unerwartete Fehler, 0 instabile und 0 übersprungene Fälle.
Separat 15 bestandene Browser-Messfälle, 11 Korpusfälle,
23 geprüfte Projekte und 0 Modulgrenzenfehler.
Die zwei erwarteten Clipboard-Fehler zählen nicht als bestanden.
