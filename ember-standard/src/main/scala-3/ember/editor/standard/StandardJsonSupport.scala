package ember.editor.standard

import ember.editor.code.*
import ember.editor.core.*
import ember.editor.image.*
import ember.editor.json.*
import ember.editor.link.*
import ember.editor.list.{ListKind, ListItemNode, ListNode}
import ember.editor.richtext.*

/** JSON codecs for the built-in node types and marks.
  *
  * ==Why they live here and not in `ember-json`==
  *
  * The same reason [[ImageJsonSupport]] does, and it is worth repeating because the temptation
  * runs the other way: §6 puts `json` '''beside''' the node modules, not above them. A server
  * that stores documents links `ember-json` and the node modules it actually uses -- not every
  * node type that exists. If the codecs lived in `ember-json`, choosing JSON would drag in
  * lists, links, code and images whether the application had them or not.
  *
  * ==Separately selectable==
  *
  * §6 forbids "eager Sammelregistrierungen". Every codec is its own value, and the bundles at
  * the bottom are a convenience, not the only door:
  *
  * {{{
  * JsonSupport.of(StandardJsonCodecs.paragraph, StandardJsonCodecs.heading)
  * StandardJsonSupport.richText          // Absaetze, Ueberschriften, Zitate, Umbrueche, Marks
  * StandardJsonSupport.everything(policy) // alles, was P12 bis P16 gebaut haben
  * }}}
  *
  * ==What a codec writes, and what it does not==
  *
  * Children are not a codec's business: [[DocumentJson]] writes the child list itself, because
  * it is the same shape for every element and because referential integrity is checked once, in
  * one place (§19.2). A codec writes what makes '''its''' node type different -- a heading's
  * level, a list's start number, a link's target.
  */
object StandardJsonCodecs:

  // -----------------------------------------------------------------------------------------
  // Rich Text
  // -----------------------------------------------------------------------------------------

  val paragraph: NodeJsonCodec[ParagraphNode] = childrenOnly(ParagraphNode)(ParagraphNode(_, _))

  val quote: NodeJsonCodec[QuoteNode] = childrenOnly(QuoteNode)(QuoteNode(_, _))

  val heading: NodeJsonCodec[HeadingNode] = new NodeJsonCodec[HeadingNode]:
    val nodeType: NodeType[HeadingNode] = HeadingNode

    def encode(
        node: HeadingNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] =
      Right(Vector("level" -> JsonValue.num(node.level.level)))

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, HeadingNode] =
      for
        raw <- payload.int("level", context.path)
        level <- HeadingLevel
          .fromInt(raw)
          .toRight(
            // §19.2 verlangt die Pruefung von Zahlenbereichen. Auf H6 zu klemmen erzeugte ein
            // Dokument, das vom Payload abweicht, ohne es zu sagen.
            DecodeError.InvalidValue(s"`$raw` ist keine Ueberschriftsebene (1 bis 6).", context.path.field("level"))
          )
      yield HeadingNode(id, Vector.empty, level)

  val breaks: NodeJsonCodec[BreakNode] = new NodeJsonCodec[BreakNode]:
    val nodeType: NodeType[BreakNode] = BreakNode

    def encode(
        node: BreakNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] =
      Right(Vector("kind" -> JsonValue.Str(node.kind.toString.toLowerCase)))

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, BreakNode] =
      for
        raw <- payload.string("kind", context.path)
        kind <- BreakKind.values
          .find(_.toString.equalsIgnoreCase(raw))
          .toRight(
            DecodeError.InvalidValue(s"`$raw` ist keine Umbruchart.", context.path.field("kind"))
          )
      yield BreakNode(id, kind)

  val thematicBreak: NodeJsonCodec[ThematicBreakNode] = new NodeJsonCodec[ThematicBreakNode]:
    val nodeType: NodeType[ThematicBreakNode] = ThematicBreakNode

    def encode(
        node: ThematicBreakNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] = Right(Vector.empty)

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, ThematicBreakNode] = Right(ThematicBreakNode(id))

  // -----------------------------------------------------------------------------------------
  // Listen
  // -----------------------------------------------------------------------------------------

  val listItem: NodeJsonCodec[ListItemNode] = childrenOnly(ListItemNode)(ListItemNode(_, _))

  val list: NodeJsonCodec[ListNode] = new NodeJsonCodec[ListNode]:
    val nodeType: NodeType[ListNode] = ListNode

    def encode(
        node: ListNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] =
      Right(
        Vector(
          "kind"  -> JsonValue.Str(node.kind.toString.toLowerCase),
          "tight" -> JsonValue.Bool(node.tight)
        ) ++
          // Die Startnummer nur, wenn sie etwas sagt: bei einer Aufzaehlung bedeutet sie nichts,
          // und eine `"start":1` in jeder Liste waere Rauschen ohne Aussage.
          Option.when(node.kind == ListKind.Ordered && node.start != 1)(
            "start" -> JsonValue.num(node.start)
          )
      )

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, ListNode] =
      for
        rawKind <- payload.string("kind", context.path)
        kind <- ListKind.values
          .find(_.toString.equalsIgnoreCase(rawKind))
          .toRight(
            DecodeError.InvalidValue(s"`$rawKind` ist keine Listenart.", context.path.field("kind"))
          )
        tight <- boolean(payload, "tight", context.path)
        start <- startNumber(payload, context.path)
      yield ListNode(id, Vector.empty, kind, start, tight)

  private def startNumber(
      payload: JsonValue.Obj,
      at: DiagnosticPath
  ): Either[DecodeError, Int] =
    payload.get("start") match
      case None | Some(JsonValue.Null) => Right(1)
      case Some(_) =>
        payload.int("start", at).flatMap { value =>
          if value >= 0 then Right(value)
          else
            Left(
              DecodeError.InvalidValue(s"`$value` ist keine Startnummer.", at.field("start"))
            )
        }

  // -----------------------------------------------------------------------------------------
  // Links
  // -----------------------------------------------------------------------------------------

  /** The link codec, with the application's policy.
    *
    * Same shape as [[ImageJsonSupport.codec]] and the same reason: §19.1 wants the same check on
    * a payload as on a dialog, and passing the policy in is what makes them one check rather
    * than two that could drift.
    */
  def link(policy: LinkUrlPolicy = LinkUrlPolicy.default): NodeJsonCodec[LinkNode] =
    new NodeJsonCodec[LinkNode]:
      val nodeType: NodeType[LinkNode] = LinkNode

      def encode(
          node: LinkNode,
          context: EncodeContext
      ): Either[EncodeError, Vector[(String, JsonValue)]] =
        Right(
          Vector("href" -> JsonValue.Str(node.target.url.value)) ++
            node.target.title.map(title => "title" -> JsonValue.Str(title)).toVector
        )

      def decode(
          id: NodeId,
          payload: JsonValue.Obj,
          context: DecodeContext
      ): Either[DecodeError, LinkNode] =
        for
          raw <- payload.string("href", context.path)
          url <- policy
            .parse(raw, context.path.field("href"))
            .left
            .map(error => DecodeError.InvalidValue(error.message, context.path.field("href")))
          title <- payload.optionalString("title", context.path)
        yield LinkNode(id, Vector.empty, LinkTarget(url, title.filter(_.trim.nonEmpty)))

  // -----------------------------------------------------------------------------------------
  // Code
  // -----------------------------------------------------------------------------------------

  val code: NodeJsonCodec[CodeBlockNode] = new NodeJsonCodec[CodeBlockNode]:
    val nodeType: NodeType[CodeBlockNode] = CodeBlockNode

    def encode(
        node: CodeBlockNode,
        context: EncodeContext
    ): Either[EncodeError, Vector[(String, JsonValue)]] =
      Right(
        node.info.language.map(value => "language" -> JsonValue.Str(value.value)).toVector ++
          node.info.meta.map(value => "meta" -> JsonValue.Str(value)).toVector
      )

    def decode(
        id: NodeId,
        payload: JsonValue.Obj,
        context: DecodeContext
    ): Either[DecodeError, CodeBlockNode] =
      for
        rawLanguage <- payload.optionalString("language", context.path)
        language <- rawLanguage match
          case None => Right(None)
          case Some(value) =>
            CodeLanguage
              .parse(value)
              .map(Some(_))
              .toRight(
                DecodeError.InvalidValue(
                  s"`$value` ist keine gueltige Sprachangabe.",
                  context.path.field("language")
                )
              )
        meta <- payload.optionalString("meta", context.path)
      yield CodeBlockNode(id, Vector.empty, CodeInfo(language, meta.filter(_.nonEmpty)))

  // -----------------------------------------------------------------------------------------
  // Marks
  // -----------------------------------------------------------------------------------------

  /** The five built-in marks. None of them carries data, so all five are the same codec.
    *
    * They are still five values and not one, because §6 asks for separate selectability and
    * because a profile that has no underline should not be able to read one from a payload.
    */
  val strong: MarkJsonCodec[TextMark]     = dataless(StandardMarks.Strong)
  val emphasis: MarkJsonCodec[TextMark]   = dataless(StandardMarks.Emphasis)
  val underline: MarkJsonCodec[TextMark]  = dataless(StandardMarks.Underline)
  val strike: MarkJsonCodec[TextMark]     = dataless(StandardMarks.Strike)
  val inlineCode: MarkJsonCodec[TextMark] = dataless(StandardMarks.InlineCode)

  // -----------------------------------------------------------------------------------------
  // Werkzeug
  // -----------------------------------------------------------------------------------------

  /** A codec for an element whose only content is its children.
    *
    * Three node types have no fields of their own, and writing the same empty codec three times
    * would be three places for a typo to hide.
    */
  private def childrenOnly[N <: ElementNode](
      kind: NodeType[N]
  )(make: (NodeId, Vector[NodeId]) => N): NodeJsonCodec[N] =
    new NodeJsonCodec[N]:
      val nodeType: NodeType[N] = kind

      def encode(node: N, context: EncodeContext): Either[EncodeError, Vector[(String, JsonValue)]] =
        Right(Vector.empty)

      def decode(
          id: NodeId,
          payload: JsonValue.Obj,
          context: DecodeContext
      ): Either[DecodeError, N] = Right(make(id, Vector.empty))

  private def dataless(mark: TextMark): MarkJsonCodec[TextMark] = new MarkJsonCodec[TextMark]:
    val markId: MarkId = mark.markId

    def project(candidate: TextMark): Option[TextMark] =
      Option.when(candidate.markId == mark.markId)(mark)

    def encode(value: TextMark): Vector[(String, JsonValue)] = Vector.empty

    def decode(payload: JsonValue.Obj, at: DiagnosticPath): Either[DecodeError, TextMark] =
      Right(mark)

  private def boolean(
      payload: JsonValue.Obj,
      name: String,
      at: DiagnosticPath
  ): Either[DecodeError, Boolean] =
    payload.get(name) match
      case Some(JsonValue.Bool(value)) => Right(value)
      case Some(other) =>
        Left(DecodeError.TypeMismatch("boolean", other.getClass.getSimpleName, at.field(name)))
      case None => Left(DecodeError.MissingField(name, at))

/** Ready-made bundles. A convenience over [[StandardJsonCodecs]], never the only way in. */
object StandardJsonSupport:

  /** The five built-in marks. */
  val marks: MarkSupport = MarkSupport.of(
    StandardJsonCodecs.strong,
    StandardJsonCodecs.emphasis,
    StandardJsonCodecs.underline,
    StandardJsonCodecs.strike,
    StandardJsonCodecs.inlineCode
  )

  /** Core plus the rich-text profile: paragraphs, headings, quotes, breaks and the marks. */
  val richText: JsonSupport =
    (CoreJsonSupport.all ++ JsonSupport.of(
      StandardJsonCodecs.paragraph,
      StandardJsonCodecs.heading,
      StandardJsonCodecs.quote,
      StandardJsonCodecs.breaks,
      StandardJsonCodecs.thematicBreak
    )).withMarks(marks)

  /** Everything P12 to P16 built.
    *
    * The policies have no defaults on purpose: a caller reaching for "everything" should still
    * have to say which URLs it trusts. A payload is exactly as untrusted as a dialog (§19.1).
    */
  def everything(
      links: LinkUrlPolicy = LinkUrlPolicy.default,
      media: MediaUrlPolicy = MediaUrlPolicy.default
  ): JsonSupport =
    richText ++ JsonSupport.of(
      StandardJsonCodecs.list,
      StandardJsonCodecs.listItem,
      StandardJsonCodecs.link(links),
      StandardJsonCodecs.code,
      ImageJsonSupport.codec(media)
    )
