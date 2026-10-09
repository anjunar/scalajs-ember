import sbt.*

/** Machine-readable facts from sbt's resolved build, not a second build.sbt parser. */
object EditorMetadata {
  private def quote(value: String): String = "\"" + value.flatMap {
    case '"' => "\\\""
    case '\\' => "\\\\"
    case c if c < ' ' => f"\\u${c.toInt}%04x"
    case c => c.toString
  } + "\""
  private def array(values: Seq[String]): String = values.map(quote).mkString("[", ",", "]")
  def write(target: File, id: String, dependencies: Seq[(String, String)], modules: Seq[String],
      allowedProjects: Seq[String], forbiddenImports: Seq[String], forbiddenModules: Seq[String],
      allowedModules: Seq[String], sourceFiles: Seq[File], publishSkip: Boolean): File = {
    val edges = dependencies.map { case (name, config) =>
      s"{\"id\":${quote(name)},\"configuration\":${quote(config)}}"
    }.mkString("[", ",", "]")
    val json = s"""{"id":${quote(id)},"dependencies":$edges,"compileModules":${array(modules.sorted)},"allowedProjects":${array(allowedProjects)},"forbiddenImports":${array(forbiddenImports)},"forbiddenModules":${array(forbiddenModules)},"allowedModules":${array(allowedModules)},"sources":${array(sourceFiles.map(_.getAbsolutePath).sorted)},"publishSkip":$publishSkip}"""
    IO.write(target, json)
    target
  }
}
