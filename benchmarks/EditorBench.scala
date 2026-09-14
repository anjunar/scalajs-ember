package ember.editor.bench

import ember.editor.core.*
import ember.editor.richtext.*
import ember.editor.list.*
import ember.editor.history.*
import ember.editor.forms.*
import ember.editor.standard.*
import ember.editor.ui.*
import ember.editor.html.RenderProfile
import ui.core.component.AbstractComponent
import ui.core.render.{DomCursor, DomNodes}
import ui.core.state.Disposable
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/** Deterministic corpora and measurements; never part of a published editor artifact. */
@JSExportTopLevel("editorBench")
object EditorBench:
  private def now(): Double = System.nanoTime().toDouble / 1000000.0
  private var current       = Option.empty[Fixture]
  private class Fixture(requested: Int, shape: String, mode: String):
    val generator  = NodeIdGenerator.sequential("bench")
    val history    = new History(HistoryConfig(HistoryLimits(maxEntries = 32)))
    val field      = EditorFields.markdown("body", MarkdownSupports.everything(), generator)
    val extensions = ExtensionResolver
      .resolve(
        Vector(RichText(generator), ListExtension(generator)) ++
          Option.when(mode == "history")(history) ++ Option.when(mode == "form")(
            FormFieldExtension(field)
          )
      )
      .toOption
      .get
    val paragraphs = math.max(1, requested / 2)
    val long       = shape == "long-leaf"
    val deep       = shape == "deep-list"
    val depth      = 32
    val target     = if long then NodeId("t0")
    else if deep then NodeId(s"t${depth - 1}")
    else NodeId(s"t${paragraphs / 2}")
    val nodes: Vector[EditorNode] =
      if deep then
        Vector(RootNode(NodeId("root"), Vector(NodeId("l0")))) ++ Vector
          .tabulate(depth) { i =>
            Vector(
              ListNode(NodeId(s"l$i"), Vector(NodeId(s"i$i")), ListKind.Unordered),
              ListItemNode(
                NodeId(s"i$i"),
                Vector(NodeId(s"p$i")) ++ Option.when(i + 1 < depth)(NodeId(s"l${i + 1}"))
              ),
              ParagraphNode(NodeId(s"p$i"), Vector(NodeId(s"t$i"))),
              TextNode(NodeId(s"t$i"), "A short paragraph.")
            )
          }
          .flatten
      else
        val count = if long then 1 else paragraphs
        Vector(RootNode(NodeId("root"), Vector.tabulate(count)(i => NodeId(s"p$i")))) ++
          Vector
            .tabulate(count)(i =>
              Vector(
                ParagraphNode(NodeId(s"p$i"), Vector(NodeId(s"t$i"))),
                TextNode(NodeId(s"t$i"), if long then "a" * 1000000 else "A short paragraph.")
              )
            )
            .flatten
    val document = Document.unsafe(extensions.schema, NodeId("root"), nodes)
    val session  =
      EditorSession.create(document, extensions, extensions.sessionConfig()).toOption.get
    var changed                                                      = 0
    var created                                                      = 0
    var disposed                                                     = 0
    var projected                                                    = Vector.empty[Double]
    var started                                                      = 0.0
    var view                                                         = Option.empty[DocumentView]
    var projections                                                  = Subscription.cancelled
    def counted[N <: EditorNode](delegate: NodeView[N]): NodeView[N] = new NodeView[N]:
      val nodeType                                                   = delegate.nodeType
      def create(node: N, profile: RenderProfile): AbstractComponent =
        created += 1
        val component = delegate.create(node, profile)
        component.addDisposable(Disposable { disposed += 1 })
        component
      def accepts(component: AbstractComponent, node: N, profile: RenderProfile): Boolean =
        delegate.accepts(component, node, profile)
      def update(component: AbstractComponent, node: N, profile: RenderProfile): Unit =
        delegate.update(component, node, profile)
    def mount(host: dom.Element): Unit =
      val support = ViewSupport.of(ImageSupport.views.views.map(v => counted(v))*)
      view = Some(DocumentView.mount(session, DomCursor.root(host), support))
      projections = view.get.onProjected(_ => projected :+= now() - started)
    def step(i: Int): Double =
      started = now()
      val commit = session
        .update(TransactionMeta.user.withHistory(HistoryPolicy.Push))(
          _.spliceText(target, 0, 1, if i % 2 == 0 then "x" else "y")
        )
        .toOption
        .get
      val elapsed = now() - started
      changed = math.max(changed, commit.changes.changedNodes.size)
      elapsed
    def close(): Unit =
      projections.dispose()
      view.foreach(_.dispose())
      session.dispose()

  private def summary(values: Vector[Double]): js.Object =
    val sorted = values.sorted
    js.Dynamic.literal(
      samples = sorted.size,
      p50Ms = sorted((sorted.size - 1) / 2),
      p95Ms = sorted(math.ceil(sorted.size * .95).toInt - 1),
      maxMs = sorted.last
    )

  @JSExport def core(requested: Int, shape: String, mode: String, iterations: Int): js.Object =
    val buildStart = now()
    val f          = new Fixture(requested, shape, mode)
    val buildMs    = now() - buildStart
    try
      (0 until 50).foreach(f.step)
      val samples = Vector.tabulate(iterations)(i => f.step(i + 50))
      val encode  = Vector.fill(5) {
        val start = now()
        val value = f.field.codec.encode(f.session.document).toOption.get
        require(value.nonEmpty)
        now() - start
      }
      js.Dynamic.literal(
        requestedNodes = requested,
        actualNodes = f.document.size,
        shape = shape,
        mode = mode,
        buildMs = buildMs,
        commit = summary(samples),
        serialize = summary(encode),
        maxChangedNodes = f.changed,
        undoEntries = f.history.state.undo.size,
        estimatedHistoryBytes = f.history.estimatedBytes
      )
    finally f.close()

  @JSExport def mount(host: dom.Element, requested: Int, shape: String): js.Object =
    dispose()
    val f = new Fixture(requested, shape, "history")
    current = Some(f)
    val start = now()
    f.mount(host)
    js.Dynamic.literal(nodes = f.document.size, mountMs = now() - start, mounts = f.created)

  @JSExport def edits(iterations: Int): js.Object =
    val f = current.get
    (0 until 50).foreach(f.step)
    val before   = f.created
    val disposed = f.disposed
    f.projected = Vector.empty
    val samples = Vector.tabulate(iterations)(i => f.step(i + 50))
    js.Dynamic.literal(
      commitIncludingProjection = summary(samples),
      commitToProjected = summary(f.projected),
      mounts = f.created - before,
      unmounts = f.disposed - disposed,
      maxChangedNodes = f.changed
    )

  @JSExport def move(): js.Object =
    val f        = current.get
    val view     = f.view.get
    val first    = f.session.document.childrenOf(f.document.rootId).head
    val old      = DomNodes.raw(view.componentFor(first).get.host)
    val mounts   = f.created
    val unmounts = f.disposed
    val start    = now()
    f.session
      .update(
        _.move(first, f.document.rootId, f.session.document.childrenOf(f.document.rootId).size - 1)
      )
      .toOption
      .get
    js.Dynamic.literal(
      elapsedMs = now() - start,
      identity = (old eq DomNodes.raw(view.componentFor(first).get.host)),
      mounts = f.created - mounts,
      unmounts = f.disposed - unmounts
    )

  @JSExport def dispose(): Unit =
    current.foreach(_.close())
    current = None
