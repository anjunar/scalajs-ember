package ember.editor.markdown

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** GFM pipe tables, under the profile that asks for them (X01). */
final class MarkdownTableSpec extends AnyFlatSpec with Matchers {

  private val tables = MarkdownProfile.commonMarkSafeWithTables

  private def parse(
      source: String,
      profile: MarkdownProfile = tables
  ): MarkdownDocument =
    Markdown.parseSyntax(source, profile).fold(error => fail(error.render), _.document)

  private def table(source: String): MarkdownBlock.Table =
    parse(source).children
      .collectFirst { case value: MarkdownBlock.Table => value }
      .getOrElse(
        fail(s"no table in: $source")
      )

  private def cells(value: MarkdownBlock.Table): Vector[Vector[String]] =
    value.children.collect { case row: MarkdownBlock.TableRow =>
      row.children.collect { case cell: MarkdownBlock.TableCell =>
        MarkdownInline.plainText(cell.inlines)
      }
    }

  "A pipe table" should "be read with its header, cells and alignments" in {
    val value = table("| a | b |\n| --- | :-: |\n| 1 | 2 |")

    cells(value) shouldBe Vector(Vector("a", "b"), Vector("1", "2"))
    value.alignments shouldBe Vector(TableAlignment.Unspecified, TableAlignment.Center)
    value.children.collect { case row: MarkdownBlock.TableRow => row.header } shouldBe
      Vector(true, false)
  }

  it should "not need outer pipes" in {
    cells(table("a | b\n--- | ---\n1 | 2")) shouldBe Vector(Vector("a", "b"), Vector("1", "2"))
  }

  it should "read every alignment" in {
    table("|a|b|c|d|\n|---|:---|:---:|---:|").alignments shouldBe Vector(
      TableAlignment.Unspecified,
      TableAlignment.Left,
      TableAlignment.Center,
      TableAlignment.Right
    )
  }

  it should "parse inline content and take an escaped pipe as a pipe" in {
    val value = table("| `a\\|b` | **x** |\n|---|---|")

    cells(value).head shouldBe Vector("a|b", "x")
    value.children.head.children(1) match
      case cell: MarkdownBlock.TableCell => cell.inlines.head shouldBe a[MarkdownInline.Strong]
      case other                         => fail(other.toString)
  }

  it should "fill missing cells and drop surplus ones" in {
    cells(table("|a|b|\n|-|-|\n|1|\n|1|2|3|")) shouldBe
      Vector(Vector("a", "b"), Vector("1", ""), Vector("1", "2"))
  }

  it should "end at a blank line" in {
    parse("|a|\n|-|\n|1|\n\nText").children.map(_.getClass.getSimpleName) shouldBe
      Vector("Table", "Paragraph")
  }

  it should "stay a paragraph when the delimiter row does not match the header" in {
    parse("|a|b|\n|-|").children.map(_.getClass.getSimpleName) shouldBe Vector("Paragraph")
  }

  it should "stay a paragraph without the extension" in {
    // The conformance suite runs under the CommonMark profile, and a table would change what this
    // source means there.
    parse("| a | b |\n| --- | --- |", MarkdownProfile.commonMarkSafe).children
      .map(_.getClass.getSimpleName) shouldBe Vector("Paragraph")
  }

  it should "work inside a list item" in {
    val item = parse("- | a |\n  | - |\n  | 1 |").children.head.children.head
    item.children.head shouldBe a[MarkdownBlock.Table]
  }

  it should "keep its spans inside the table's" in {
    val result = Markdown.parseSyntax("x\n\n| a | b |\n| - | - |\n| 1 | 2 |", tables).toOption.get
    val value  = result.document.children.collectFirst { case t: MarkdownBlock.Table => t }.get

    value.children.foreach { row =>
      value.span.containsSpan(row.span) shouldBe true
      row.children.foreach(cell => row.span.containsSpan(cell.span) shouldBe true)
    }
  }

  "Writing a table" should "produce outer pipes and the delimiter row" in {
    MarkdownWriter.write(parse("a | b\n:- | -:\n1 | 2")) shouldBe
      "| a | b |\n| :--- | ---: |\n| 1 | 2 |\n"
  }

  it should "escape pipes in cells" in {
    MarkdownWriter.write(parse("| a\\|b |\n|---|")) shouldBe "| a\\|b |\n| --- |\n"
  }

  it should "read back as the same table" in {
    val source  = "| a | *b* | `c\\|d` |\n| :-: | --- | ---: |\n| 1 |  | 3 |"
    val first   = table(source)
    val written = MarkdownWriter.write(parse(source))
    val second  = table(written)

    cells(second) shouldBe cells(first)
    second.alignments shouldBe first.alignments
    MarkdownWriter.write(parse(written)) shouldBe written
  }
}
