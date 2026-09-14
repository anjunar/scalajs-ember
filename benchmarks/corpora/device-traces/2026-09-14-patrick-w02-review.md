# W02: japanische IME unter Windows

Status: **Japanische Eingabe und Bestätigung laut Patrick bestanden;
Undo/Redo am selben Dokument zusätzlich automatisiert bestanden.**
Der Lauf belegt diese Teilprüfung im Codex In-app Browser. W02 in seiner gesamten
manuellen Testvorgabe (einschließlich Eingabe mitten im Bestandstext) bleibt offen.

Patrick bestätigt nach der Anleitung für Eingabe, Kandidatenbestätigung und
Strg+Z/Strg+Y: **„funktioniert einwandfrei :-)“**. Das Formular wurde anschließend
in seinem Auftrag ausgefüllt und der Trace exportiert. Die allgemeine positive
Rückmeldung wird nicht als einzeln beobachteter Nachweis aller Tastenkürzel gewertet.

## Original und Umgebung

- [Unveränderter Geräte-Trace](2026-09-14-patrick-w02-japanese.json), 133 Ereignisse,
  nicht abgeschnitten; 14.09.2026, 18:55:55.537–18:57:35.200 MESZ.
- SHA-256: `0aca9e49fd68f632da493d4c7b581252be41b5a1577136f61d1de28607409881`.
- Patrick; PC mit Windows 11 laut Formular; Codex In-app Browser,
  UA Chrome/152.0.0.0, Win64/x64. Kein Nachweis für eigenständiges Chrome 153.
- Japanisch laut Patrick installiert, Hiragana-/Kanji-Eingabe im Trace sichtbar.
  Microsoft IME war angeleitet; genaue IME-Version nicht aus dem Trace bestimmbar.
  Das ältere Vorbereitungsfeld „Microsoft IME / Hiragana im Test prüfen“ bleibt
  im Original erhalten.
- Build `0381575e6c759875a7ee2aa2d80b8a7d13a8a270`, dirty=true;
  Integration-SHA `0973505cfd8f5239a6e60299887b9d2c02327b983716f273ef90f1dc047e85f4`;
  UI-Core 1.0.1. Während des Laufs weder Neuladen noch Neubau durch Codex.

## Technische Auswertung

Vier Composition-Starts, 20 Updates und vier Ends; jeweils 20 `beforeinput` und
`input`, 36 `keydown`, 21 Selectionchanges sowie je vier Focusin/-out.
129 Ereignisse sind trusted; alle vier `compositionend` sind als untrusted
aufgezeichnet. Ihre Herkunft ist durch den Trace nicht erklärt. Diese Besonderheit
bleibt dokumentiert; der Gerätebeleg beruht auf Patricks tatsächlicher Eingabe und
Rückmeldung zusammen mit dem Verlauf, nicht allein auf `isTrusted`.

Der erste Wortversuch wechselt von Romaji-Zwischenständen zu `日本`, zurück zu
`にほん` und endet leer. Der zweite bestätigt `日本語` vor einem vollen Leerzeichen
U+3000. Endtext: `Hello world日本語　`. Sämtliche Textfelder enthalten nur Fixture-
und japanischen Testtext einschließlich korrigierter Zwischenstände.

In allen 133 Snapshots **nach** der Ereignisverarbeitung stimmen DOM und Modell
überein. Bei 18 nativen `input`-Ereignissen ist im Capture-Snapshot der DOM-Text
bereits geändert, während das Modell noch den vorherigen Text enthält; nach dem
Editor-Handler ist die Änderung jeweils übernommen. Keine bleibende Abweichung
oder doppelte Übernahme festgestellt.

Nach der letzten Bestätigung enthält der Geräte-Trace nur Selection-/Fokusereignisse.
Die vorherigen Control-/Process-Tasten und `y`-Ereignisse belegen kein Undo/Redo der
abschließend bestätigten Composition. Auch die genaue Abbruchtaste des ersten
Versuchs und das Kandidatenfenster sind daraus nicht rekonstruierbar.

## Ergänzende Undo-/Redo-Prüfung

Codex hat nach Sicherung des Originals im selben Dokument einen getrennten
[automatisierten Trace](../../results/p28-codex-ime-undo-2026-09-14.json) aufgenommen:
neun Ereignisse; SHA-256
`aca0ef368d86bef069d3fe69866d137434fee7c2d5beaf9e0801d30521e12b18`.
Ein Strg+Z entfernt `日本語` vollständig und erhält `Hello world　`.
Ein Strg+Y stellt `Hello world日本語　` einmal wieder her. UI und Trace bestätigen
beide Zustände, DOM und Modell stimmen überein. Keine erneute Texteingabe oder
synthetische Composition wurde dafür erzeugt. Der Endtext bleibt erhalten.

Dies ist ein ergänzender automatisierter Check an einer echten Composition,
keine nachträglich behauptete manuelle Undo-/Redo-Abnahme. Das Exportformat
`kind=operator-device-trace` ändert diese Einordnung nicht.

W03 (gezielter Abbruch samt Folgetippen), W04 (Fokuswechsel während Composition),
W05 (Auswahl/Formatgrenzen) und die übrigen Geräte-/Screenreaderprüfungen bleiben
offen. P29 wird nicht begonnen.
