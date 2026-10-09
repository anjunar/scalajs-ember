// Dieselbe Ursache wie das `exportJars := false` in der Build-Wurzel, nur eine Ebene
// hoeher: sbt 2 setzt `exportJars := true` per Default, aendert man build.sbt oder eine
// Datei in project/, packt sbt die Meta-Build-JAR (scalajs-ember-build_sbt2_3) neu.
// `packageBin` schreibt erst .tmp und dann `Files.move`; der laufende sbt-Server haelt
// die JAR aber offen, und unter Windows laesst sich eine offene Datei nicht per Rename
// ersetzen. Ergebnis war reproduzierbar
//
//   java.nio.file.AccessDeniedException:
//     ...\scalajs-ember-build_sbt2_3-0.1.0-SNAPSHOT.jar.8f746ff2.tmp
//       -> ...\scalajs-ember-build_sbt2_3-0.1.0-SNAPSHOT.jar
//
// Das blanke `exportJars := false` der Build-Wurzel gilt hier nicht -- die Meta-Build
// ist ein eigener Build. Mit Klassenverzeichnissen statt JARs entfaellt das Problem.
exportJars := false
