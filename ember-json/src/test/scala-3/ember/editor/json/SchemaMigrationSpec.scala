package ember.editor.json

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Schema-Migration (Architektur §19.2).
  *
  * Die Suite prueft die Kette nicht isoliert, sondern am Ende auch durch das Envelope hindurch:
  * eine Migration, die zwar eine schoene Umformung liefert, aber vor dem Dekodieren nicht laeuft,
  * waere nutzlos.
  */
final class SchemaMigrationSpec extends AnyFlatSpec with Matchers {

  private val root   = NodeId("root")
  private val schema = Schema.unsafe(RootNode, TextNode, BlockNode)

  private val support = CoreJsonSupport.all ++ JsonSupport.of(TestCodecs.block)

  /** Ein Envelope in Version `version`, dessen Block die genannte Wire-ID traegt. */
  private def envelope(version: Int, blockType: String, label: String = "Abschnitt"): String =
    s"""{"format":"ember-document","formatVersion":1,"schemaVersion":$version,"root":"root",""" +
      s""""nodes":[{"id":"root","type":"ember.core.root/1","codecVersion":1,"children":["b0"]},""" +
      s"""{"id":"b0","type":"$blockType","codecVersion":1,"label":"$label","children":[]}]}"""

  /** Benennt eine Knotenart um -- der haeufigste Migrationsfall ueberhaupt. */
  private def rename(from: Int, to: Int, oldType: String, newType: String): SchemaMigration =
    SchemaMigration(
      from,
      to,
      value =>
        value.get("nodes") match
          case Some(JsonValue.Arr(items)) =>
            Right(
              JsonValue.Obj(
                value.fields.map {
                  case ("nodes", _) => "nodes" -> JsonValue.Arr(items.map(retype(oldType, newType)))
                  case other        => other
                }
              )
            )
          case _ => Left("Das Envelope hat keine Knotenliste.")
    )

  private def retype(oldType: String, newType: String)(item: JsonValue): JsonValue = item match
    case node: JsonValue.Obj =>
      JsonValue.Obj(node.fields.map {
        case ("type", JsonValue.Str(value)) if value == oldType =>
          "type" -> JsonValue.Str(newType)
        case other => other
      })
    case other => other

  private def decode(
      source: String,
      migrations: SchemaMigrations,
      target: Int = 2
  ): Either[Vector[DecodeError], DecodeResult] =
    DocumentJson.decodeString(
      source,
      schema,
      support,
      DecodeConfig(schemaVersion = target, migrations = migrations)
    )

  // ---------------------------------------------------------------------------------------
  // Die Kette
  // ---------------------------------------------------------------------------------------

  "A migration" should "lift an older payload to the target version" in {
    val chain = SchemaMigrations.unsafe(rename(1, 2, "test.section/1", "test.block/1"))

    val result = decode(envelope(1, "test.section/1"), chain)

    result.map(_.document.node(NodeId("b0"))) shouldBe
      Right(Some(BlockNode(NodeId("b0"), Vector.empty, "Abschnitt")))
  }

  it should "run before the nodes are decoded" in {
    // Der Punkt der vorigen Zusicherung, andersherum belegt: ohne Migration kennt das Schema
    // die alte Wire-ID nicht, und das Dekodieren scheitert genau daran.
    val outcome = decode(envelope(1, "test.section/1"), SchemaMigrations.none)

    outcome match
      case Left(Vector(error: DecodeError.MissingMigration)) =>
        error.from shouldBe 1
        error.to shouldBe 2
      case other => fail(s"unerwartet: $other")
  }

  it should "chain several steps" in {
    val chain = SchemaMigrations.unsafe(
      rename(1, 2, "test.section/1", "test.part/1"),
      rename(2, 3, "test.part/1", "test.block/1")
    )

    decode(envelope(1, "test.section/1"), chain, target = 3)
      .map(_.document.size) shouldBe Right(2)
  }

  it should "record the version it reached" in {
    // Die Buchhaltung ueber `schemaVersion` gehoert der Kette, nicht dem einzelnen Schritt --
    // sonst koennte ein Schritt sie vergessen, und die Kette liefe im Kreis.
    val chain  = SchemaMigrations.unsafe(rename(1, 2, "test.section/1", "test.block/1"))
    val parsed = JsonText.parse(envelope(1, "test.section/1"), DecodeLimits.default)

    val migrated = parsed match
      case Right(value: JsonValue.Obj) => chain(value, 1, 2)
      case other                       => fail(s"unerwartet: $other")

    migrated.map(_.get("schemaVersion")) shouldBe Right(Some(JsonValue.Num(2)))
  }

  it should "leave a current payload untouched" in {
    val chain = SchemaMigrations.unsafe(rename(1, 2, "test.section/1", "test.block/1"))

    decode(envelope(2, "test.block/1"), chain).map(_.document.size) shouldBe Right(2)
  }

  // ---------------------------------------------------------------------------------------
  // Fehlende und ungueltige Pfade
  // ---------------------------------------------------------------------------------------

  "A missing path" should "be an error, not a silent pass" in {
    // §19.2: "fehlende Migrationspfade sind Fehler". Ein Payload der Version 1 als Version 3 zu
    // lesen hiesse, seine Felder nach heutigen Regeln zu deuten -- Datenverlust ohne Meldung.
    val chain = SchemaMigrations.unsafe(rename(2, 3, "test.part/1", "test.block/1"))

    decode(envelope(1, "test.section/1"), chain, target = 3) match
      case Left(Vector(error: DecodeError.MissingMigration)) => error.from shouldBe 1
      case other                                             => fail(s"unerwartet: $other")
  }

  it should "reject a step that overshoots the target" in {
    // Ein Sprung 1->3 hebt ein Dokument in eine Version, die der Aufrufer nicht angefordert hat.
    val chain = SchemaMigrations.unsafe(rename(1, 3, "test.section/1", "test.block/1"))

    decode(envelope(1, "test.section/1"), chain, target = 2) match
      case Left(Vector(_: DecodeError.MissingMigration)) => succeed
      case other                                         => fail(s"unerwartet: $other")
  }

  it should "refuse to downgrade" in {
    decode(envelope(3, "test.block/1"), SchemaMigrations.none, target = 2) match
      case Left(Vector(error: DecodeError.MissingMigration)) =>
        error.from shouldBe 3
        error.to shouldBe 2
      case other => fail(s"unerwartet: $other")
  }

  "A failing migration" should "report its step and reason" in {
    val chain = SchemaMigrations.unsafe(
      SchemaMigration(1, 2, _ => Left("Der Payload nennt kein Layout."))
    )

    decode(envelope(1, "test.section/1"), chain) match
      case Left(Vector(error: DecodeError.MigrationFailed)) =>
        error.from shouldBe 1
        error.to shouldBe 2
        error.message should include("Layout")
      case other => fail(s"unerwartet: $other")
  }

  // ---------------------------------------------------------------------------------------
  // Aufbau der Kette
  // ---------------------------------------------------------------------------------------

  "A chain" should "reject a step that runs backwards" in {
    SchemaMigrations.of(SchemaMigration(3, 2, Right(_))) match
      case Left(Vector(error: MigrationSetupError.NotAscending)) => error.from shouldBe 3
      case other                                                 => fail(s"unerwartet: $other")
  }

  it should "reject a step that stands still" in {
    SchemaMigrations.of(SchemaMigration(2, 2, Right(_))) match
      case Left(Vector(_: MigrationSetupError.NotAscending)) => succeed
      case other                                             => fail(s"unerwartet: $other")
  }

  it should "reject two steps reading the same version" in {
    // Welche von zwei moeglichen Umformungen ein Dokument erfaehrt, darf nicht von einer
    // Registrierungsreihenfolge abhaengen.
    SchemaMigrations.of(
      SchemaMigration(1, 2, Right(_)),
      SchemaMigration(1, 3, Right(_))
    ) match
      case Left(Vector(error: MigrationSetupError.AmbiguousStep)) => error.from shouldBe 1
      case other                                                  => fail(s"unerwartet: $other")
  }

  it should "accept an ascending chain" in {
    SchemaMigrations
      .of(
        SchemaMigration(1, 2, Right(_)),
        SchemaMigration(2, 5, Right(_))
      )
      .map(_.steps.length) shouldBe Right(2)
  }
}
