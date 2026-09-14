package ember.editor.core

import ember.editor.history.*
import scala.collection.immutable.AbstractMap
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Counts actual node-map iteration, including maps produced by subsequent operations. */
class LocalHistoryCostSpec extends AnyFlatSpec with Matchers:
  private class Counter:
    var scans = 0
  private class ObservedMap[V](underlying: Map[NodeId, V], counter: Counter)
      extends AbstractMap[NodeId, V]:
    override def size                   = underlying.size
    def get(key: NodeId): Option[V]     = underlying.get(key)
    def iterator: Iterator[(NodeId, V)] =
      counter.scans += 1
      underlying.iterator
    def removed(key: NodeId): Map[NodeId, V] = new ObservedMap(underlying.removed(key), counter)
    def updated[V1 >: V](key: NodeId, value: V1): Map[NodeId, V1] =
      new ObservedMap(underlying.updated(key, value), counter)

  for mode <- Vector("push", "merge", "group", "ignored-between-merge") do
    "Local history" should s"avoid node-map traversal for $mode at 100k nodes" in {
      val history  = new History(HistoryConfig(HistoryLimits(maxEntries = 8)))
      val resolved = ExtensionResolver.resolve(Vector(TestNodes, history)).toOption.get
      val blocks   = Vector.tabulate(50000)(i => NodeId(s"p$i"))
      val nodes    = Vector(RootNode(NodeId("root"), blocks)) ++ blocks.zipWithIndex.flatMap {
        (id, i) =>
          Vector(BlockNode(id, Vector(NodeId(s"t$i"))), TextNode(NodeId(s"t$i"), "hello"))
      }
      val original = Document.unsafe(resolved.schema, NodeId("root"), nodes)
      val counter  = new Counter()
      val observed = Document.trusted(
        original.schema,
        original.rootId,
        new ObservedMap(original.byId, counter),
        original.parents
      )
      val session = EditorSession.create(observed, resolved, resolved.sessionConfig()).toOption.get
      counter.scans = 0
      if mode == "group" then history.beginGroup()
      (0 until 10).foreach { i =>
        if mode == "ignored-between-merge" then
          session
            .update(TransactionMeta.user.withHistory(HistoryPolicy.Ignore))(
              _.spliceText(NodeId("t100"), 0, 1, if i % 2 == 0 then "a" else "b")
            )
            .isRight shouldBe true
        val meta = TransactionMeta.user.withHistory(
          if mode == "merge" || mode == "ignored-between-merge" then HistoryPolicy.Merge
          else HistoryPolicy.Push
        )
        session
          .update(meta)(_.spliceText(NodeId("t25000"), 0, 1, if i % 2 == 0 then "x" else "y"))
          .isRight shouldBe true
      }
      if mode == "group" then history.endGroup()
      counter.scans shouldBe 0
      history.canUndo shouldBe true
      history.estimatedBytes should be > 0
      if mode == "ignored-between-merge" then
        val entry = history.state.undo.last
        entry.estimatedBytes shouldBe HistoryEntry.estimate(
          entry.before.document,
          entry.after.document
        )
      session.dispose()
    }

  "Disposing a session" should "release snapshots held by its history extension" in {
    val f = new HistoryFixture()
    f.typeChar("x")
    f.history.canUndo shouldBe true
    f.session.dispose()
    f.history.state shouldBe HistoryState.empty
  }
