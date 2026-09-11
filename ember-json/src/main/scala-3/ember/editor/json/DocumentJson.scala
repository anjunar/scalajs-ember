package ember.editor.json

import ember.editor.core.*

import scala.annotation.tailrec

/** Das Ergebnis eines erfolgreichen Dekodierens.
  *
  * §9: "SSR rendert ein Document ohne lokale Selection, History oder Fokus." Genau deshalb steht
  * hier ein [[Document]] und keine Sitzung -- Auswahl, History und View-State sind Zustand einer
  * Bearbeitung, nicht Inhalt einer Datei (§5). Wer eine Sitzung will, baut sie daraus.
  */
final case class DecodeResult(document: Document, diagnostics: Vector[DecodeDiagnostic])

/** Einstellungen fuer das Dekodieren.
  *
  * @param schemaVersion
  *   die Version, die diese Anwendung versteht. Ein aelterer Payload wird ueber [[migrations]]
  *   darauf gehoben.
  * @param unknownNodes
  *   was mit unbekannten Knotenarten geschieht. Voreinstellung [[UnknownNodePolicy.Strict]] --
  *   §19.2 verlangt eine ausdrueckliche Wahl fuer alles andere.
  */
final case class DecodeConfig(
    schemaVersion: Int = 1,
    limits: DecodeLimits = DecodeLimits.default,
    unknownNodes: UnknownNodePolicy = UnknownNodePolicy.Strict,
    migrations: SchemaMigrations = SchemaMigrations.none
)

/** Das versionierte Dokumentformat.
  *
  * ==Das Envelope==
  *
  * {{{
  * {
  *   "format": "ember-document",
  *   "formatVersion": 1,
  *   "schemaVersion": 1,
  *   "root": "root",
  *   "nodes": [
  *     {"id":"root","type":"ember.core.root/1","codecVersion":1,"children":["p0"]},
  *     {"id":"p0","type":"ember.core.text/1","codecVersion":1,"text":"Hallo"}
  *   ]
  * }
  * }}}
  *
  * §19.2 zaehlt den Inhalt auf: "Formatname, Formatversion, Schemaversion, Root-ID und typisierte
  * Node-Eintraege einschliesslich Child-IDs. IDs werden fuer Persistenz/SSR erhalten."
  *
  * ==Warum die Knoten ein Array sind==
  *
  * Ein Objekt, das nach ID schluesselt, waere kompakter. Es waere aber auch blind gegenueber dem
  * Fehler, auf den es hier am meisten ankommt: `js.JSON.parse` fasst doppelte Schluessel zusammen,
  * eine doppelte Knoten-ID verschwaende also spurlos, und das Dokument saehe gueltig aus. Als Array
  * bleibt sie sichtbar und wird zu [[DecodeError.DuplicateNodeId]].
  *
  * ==Was hier nicht noch einmal geprueft wird==
  *
  * Referenzielle Integritaet, Zyklen, mehrfache Eltern, Erreichbarkeit und Schemakonformitaet
  * pruefen nicht diese Datei, sondern [[Document.build]]. Ein zweiter Validator waere eine zweite
  * Wahrheit ueber dieselbe Frage -- und die beiden liefen frueher oder spaeter auseinander. Was
  * hier geprueft wird, ist alles, was der Kern gar nicht sehen kann: Typen, Zahlenbereiche, Limits,
  * doppelte IDs im Payload, Versionen.
  */
object DocumentJson:

  val formatName: String = "ember-document"

  /** Die Version des Envelopes selbst. Steigt nur, wenn sich diese Struktur aendert. */
  val formatVersion: Int = 1

  private[json] val schemaVersionField = "schemaVersion"

  // -----------------------------------------------------------------------------------------
  // Kodieren
  // -----------------------------------------------------------------------------------------

  /** Schreibt ein Dokument.
    *
    * Die Knoten stehen in Dokumentordnung. Nicht aus Schoenheit: die Ausgabe muss bei gleichem
    * Dokument byteweise gleich sein, sonst sind Roundtrip-Fixtures und ein Vergleich zwischen
    * Server- und Browserstand wertlos.
    */
  def encode(
      document: Document,
      support: JsonSupport,
      schemaVersion: Int = 1
  ): Either[Vector[EncodeError], JsonValue.Obj] =
    val at      = DiagnosticPath.field("nodes")
    val written = document.inDocumentOrder.zipWithIndex.map { (node, index) =>
      encodeNode(node, support, at.index(index).node(node.id.value))
    }.toVector

    val failures = written.collect { case Left(error) => error }
    if failures.nonEmpty then Left(failures)
    else
      Right(
        JsonValue.obj(
          "format"           -> JsonValue.Str(formatName),
          "formatVersion"    -> JsonValue.num(formatVersion),
          schemaVersionField -> JsonValue.num(schemaVersion),
          "root"             -> JsonValue.Str(document.rootId.value),
          "nodes"            -> JsonValue.Arr(written.collect { case Right(value) => value })
        )
      )

  /** Wie [[encode]], gleich als Text. */
  def encodeToString(
      document: Document,
      support: JsonSupport,
      schemaVersion: Int = 1
  ): Either[Vector[EncodeError], String] =
    encode(document, support, schemaVersion).map(JsonText.render)

  private def encodeNode(
      node: EditorNode,
      support: JsonSupport,
      at: DiagnosticPath
  ): Either[EncodeError, JsonValue.Obj] =
    val described: Either[EncodeError, (NodeTypeId, Int, Vector[(String, JsonValue)])] =
      node match
        // Der eine Knoten, dessen Wire-Name nicht vom Codec kommt, sondern von ihm selbst --
        // das ist der Sinn der Erhaltung (§19.2).
        case unsupported: UnsupportedNode =>
          Right((unsupported.typeId, unsupported.codecVersion, unsupported.payload.fields))
        case _ => support.encode(node, at)

    described.map { (typeId, codecVersion, payload) =>
      val head = Vector(
        "id"           -> JsonValue.Str(node.id.value),
        "type"         -> JsonValue.Str(typeId.value),
        "codecVersion" -> JsonValue.num(codecVersion)
      )
      val children = node match
        case element: ElementNode =>
          Vector("children" -> JsonValue.arr(element.children.map(id => JsonValue.Str(id.value))))
        case _ => Vector.empty
      JsonValue.Obj(head ++ payload ++ children)
    }

  // -----------------------------------------------------------------------------------------
  // Dekodieren
  // -----------------------------------------------------------------------------------------

  /** Liest Text. Der uebliche Weg -- er schliesst die Plattformgrenze ein. */
  def decodeString(
      source: String,
      schema: Schema,
      support: JsonSupport,
      config: DecodeConfig = DecodeConfig()
  ): Either[Vector[DecodeError], DecodeResult] =
    JsonText.parse(source, config.limits) match
      case Left(error)  => Left(Vector(error))
      case Right(value) => decode(value, schema, support, config)

  /** Liest einen bereits konvertierten Wert. */
  def decode(
      value: JsonValue,
      schema: Schema,
      support: JsonSupport,
      config: DecodeConfig
  ): Either[Vector[DecodeError], DecodeResult] =
    val outcome =
      for
        envelope <- value.asObject(DiagnosticPath.Root)
        _        <- checkFormat(envelope)
        migrated <- migrate(envelope, config)
        rootId   <- readRootId(migrated)
        entries  <- readEntries(migrated, config)
      yield (rootId, entries)

    outcome match
      case Left(error)              => Left(Vector(error))
      case Right((rootId, entries)) => build(rootId, entries, schema, support, config)

  private def checkFormat(envelope: JsonValue.Obj): Either[DecodeError, Unit] =
    for
      name    <- envelope.string("format", DiagnosticPath.Root)
      _       <- Either.cond(name == formatName, (), DecodeError.UnknownFormat(name))
      version <- envelope.int("formatVersion", DiagnosticPath.Root)
      _       <- Either.cond(
        version == formatVersion,
        (),
        DecodeError.UnsupportedFormatVersion(version, formatVersion)
      )
    yield ()

  private def migrate(
      envelope: JsonValue.Obj,
      config: DecodeConfig
  ): Either[DecodeError, JsonValue.Obj] =
    envelope
      .int(schemaVersionField, DiagnosticPath.Root)
      .flatMap(found => config.migrations(envelope, found, config.schemaVersion))

  private def readRootId(envelope: JsonValue.Obj): Either[DecodeError, NodeId] =
    envelope
      .string("root", DiagnosticPath.Root)
      .flatMap(value =>
        NodeId
          .parse(value)
          .toRight(
            DecodeError
              .InvalidValue(s"Keine gueltige NodeId: `$value`", DiagnosticPath.field("root"))
          )
      )

  /** Ein roher Knoteneintrag: gepruefte Huelle, ungepruefte Nutzlast. */
  private final case class Entry(
      id: NodeId,
      typeId: NodeTypeId,
      codecVersion: Int,
      children: Vector[NodeId],
      payload: JsonValue.Obj,
      at: DiagnosticPath
  )

  private val envelopeFields = Set("id", "type", "codecVersion", "children")

  private def readEntries(
      envelope: JsonValue.Obj,
      config: DecodeConfig
  ): Either[DecodeError, Vector[Entry]] =
    val at = DiagnosticPath.field("nodes")
    envelope.array("nodes", DiagnosticPath.Root).flatMap { items =>
      if items.length > config.limits.maxNodes then
        Left(DecodeError.LimitExceeded("maxNodes", config.limits.maxNodes, items.length, at))
      else
        val seen = scala.collection.mutable.HashSet.empty[NodeId]
        first(items.zipWithIndex) { (item, index) =>
          readEntry(item, at.index(index), config).flatMap { entry =>
            if seen.add(entry.id) then Right(entry)
            else Left(DecodeError.DuplicateNodeId(entry.id.value, entry.at))
          }
        }
    }

  private def readEntry(
      item: JsonValue,
      at: DiagnosticPath,
      config: DecodeConfig
  ): Either[DecodeError, Entry] =
    for
      payload <- item.asObject(at)
      idValue <- payload.string("id", at)
      id      <- NodeId
        .parse(idValue)
        .toRight(DecodeError.InvalidValue(s"Keine gueltige NodeId: `$idValue`", at.field("id")))
      here = at.node(id.value)
      typeValue <- payload.string("type", here)
      typeId    <- NodeTypeId
        .parse(typeValue)
        .toRight(
          DecodeError.InvalidValue(s"Keine gueltige NodeTypeId: `$typeValue`", here.field("type"))
        )
      codecVersion <- payload.int("codecVersion", here)
      children     <- readChildren(payload, here, config)
    yield Entry(
      id,
      typeId,
      codecVersion,
      children,
      JsonValue.Obj(payload.fields.filterNot((name, _) => envelopeFields.contains(name))),
      here
    )

  private def readChildren(
      payload: JsonValue.Obj,
      at: DiagnosticPath,
      config: DecodeConfig
  ): Either[DecodeError, Vector[NodeId]] =
    payload.get("children") match
      case None | Some(JsonValue.Null) => Right(Vector.empty)
      case Some(value)                 =>
        val here = at.field("children")
        value.asArray(here).flatMap { items =>
          if items.length > config.limits.maxChildren then
            Left(
              DecodeError
                .LimitExceeded("maxChildren", config.limits.maxChildren, items.length, here)
            )
          else
            first(items.zipWithIndex) { (item, index) =>
              item.asString(here.index(index)).flatMap { text =>
                NodeId
                  .parse(text)
                  .toRight(
                    DecodeError.InvalidValue(s"Keine gueltige NodeId: `$text`", here.index(index))
                  )
              }
            }
        }

  // -----------------------------------------------------------------------------------------
  // Aus den Eintraegen ein Dokument
  // -----------------------------------------------------------------------------------------

  private def build(
      rootId: NodeId,
      entries: Vector[Entry],
      schema: Schema,
      support: JsonSupport,
      config: DecodeConfig
  ): Either[Vector[DecodeError], DecodeResult] =
    val built    = entries.map(entry => entry -> buildNode(entry, schema, support, config))
    val failures = built.collect { case (_, Left(error)) => error }

    // Alle Knotenfehler auf einmal. Wer einen fremden Payload debuggt, will nicht zwanzig Laeufe
    // brauchen, um zwanzig Tippfehler zu finden -- und ein teilweise gueltiges Dokument entsteht
    // dabei ohnehin nicht (Abnahme P10).
    if failures.nonEmpty then Left(failures)
    else
      val nodes       = built.collect { case (_, Right(node)) => node }
      val diagnostics = built.collect { case (entry, Right(_: UnsupportedNode)) =>
        DecodeDiagnostic(
          s"Die Knotenart `${entry.typeId.value}` ist unbekannt und wurde als UnsupportedNode " +
            "erhalten. Sie ist nicht bearbeitbar (§19.2).",
          entry.at
        )
      }

      val effective =
        if nodes.exists(_.isInstanceOf[UnsupportedNode]) then
          schema.extendedWith(UnsupportedNode).getOrElse(schema)
        else schema

      Document.build(effective, rootId, nodes) match
        case Left(violations) => Left(Vector(DecodeError.InvalidDocument(violations)))
        case Right(document)  =>
          checkDepth(document, config.limits)
            .map(_ => DecodeResult(document, diagnostics))
            .left
            .map(Vector(_))

  private def buildNode(
      entry: Entry,
      schema: Schema,
      support: JsonSupport,
      config: DecodeConfig
  ): Either[DecodeError, EditorNode] =
    schema.byId(entry.typeId) match
      case None =>
        config.unknownNodes match
          case UnknownNodePolicy.Strict =>
            Left(DecodeError.UnknownNodeType(entry.typeId.value, entry.at))
          case UnknownNodePolicy.Preserve =>
            Right(
              UnsupportedNode(
                entry.id,
                entry.typeId,
                entry.codecVersion,
                entry.payload,
                entry.children,
                UnsupportedNode.fallbackOf(entry.payload)
              )
            )
      case Some(descriptor) =>
        support.codecFor(descriptor.typeId) match
          // Eine bekannte Art ohne Codec ist kein unbekanntes Datum, sondern ein Verdrahtungsfehler
          // der Anwendung. Ihn unter `Preserve` zu verstecken hiesse, ein Dokument als fremd
          // auszugeben, das dieser Editor sehr wohl versteht -- und es damit unbearbeitbar zu
          // machen.
          case None        => Left(DecodeError.NoCodec(entry.typeId.value, entry.at))
          case Some(codec) =>
            for
              _    <- checkCodecVersion(entry, codec)
              node <- support.decode(
                codec,
                entry.id,
                entry.payload,
                entry.codecVersion,
                config.limits,
                entry.at
              )
              placed <- attachChildren(node, entry, descriptor)
            yield placed

  private def checkCodecVersion(
      entry: Entry,
      codec: NodeJsonCodec[?]
  ): Either[DecodeError, Unit] =
    // Aeltere Staende darf ein Codec lesen -- er bekommt die Version im `DecodeContext` und kann
    // verzweigen. Ein *neuerer* Payload kann dagegen Felder tragen, deren Bedeutung dieser Stand
    // nicht kennt; ihn als alten zu lesen waere stiller Datenverlust.
    if entry.codecVersion <= codec.codecVersion then Right(())
    else
      Left(
        DecodeError.UnsupportedCodecVersion(
          entry.typeId.value,
          entry.codecVersion,
          codec.codecVersion,
          entry.at
        )
      )

  private def attachChildren(
      node: EditorNode,
      entry: Entry,
      descriptor: NodeType[?]
  ): Either[DecodeError, EditorNode] =
    (node, descriptor) match
      case (_, _) if entry.children.isEmpty && !node.isInstanceOf[ElementNode] => Right(node)
      case (element: ElementNode, elementType: ElementNodeType[?])             =>
        Right(withChildren(elementType, element, entry.children))
      case (_: ElementNode, _) =>
        Left(
          DecodeError.InvalidValue(
            s"`${entry.typeId.value}` hat Kinder, sein Deskriptor ist aber kein " +
              "ElementNodeType -- die Kindliste liesse sich nicht setzen (§8.1).",
            entry.at
          )
        )
      case _ =>
        Left(
          DecodeError.InvalidValue(
            s"`${entry.typeId.value}` ist ein Blatt, der Payload nennt aber " +
              s"${entry.children.length} Kinder.",
            entry.at.field("children")
          )
        )

  private def withChildren[N <: ElementNode](
      descriptor: ElementNodeType[N],
      node: ElementNode,
      children: Vector[NodeId]
  ): EditorNode =
    descriptor.project(node).map(descriptor.withChildren(_, children)).getOrElse(node)

  /** Die Tiefe des Dokumentbaums.
    *
    * Erst nach [[Document.build]]: vorher ist die Kindstruktur nur eine Behauptung des Payloads,
    * und eine Tiefenmessung auf einem Zyklus liefe nicht zu Ende.
    */
  private def checkDepth(document: Document, limits: DecodeLimits): Either[DecodeError, Unit] =
    @tailrec
    def walk(level: Vector[NodeId], depth: Int): Either[DecodeError, Unit] =
      if level.isEmpty then Right(())
      else if depth > limits.maxDocumentDepth then
        Left(
          DecodeError
            .LimitExceeded("maxDocumentDepth", limits.maxDocumentDepth, depth, DiagnosticPath.Root)
        )
      else
        walk(
          level.flatMap(id =>
            document
              .node(id)
              .collect { case element: ElementNode => element.children }
              .getOrElse(Vector.empty)
          ),
          depth + 1
        )

    walk(Vector(document.rootId), 1)

  /** Der erste Fehler bricht ab. */
  private def first[A, B](items: Vector[A])(
      step: A => Either[DecodeError, B]
  ): Either[DecodeError, Vector[B]] =
    val built                        = Vector.newBuilder[B]
    val iterator                     = items.iterator
    var failure: Option[DecodeError] = None
    while iterator.hasNext && failure.isEmpty do
      step(iterator.next()) match
        case Right(value) => built += value
        case Left(error)  => failure = Some(error)
    failure.toLeft(built.result())
