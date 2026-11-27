package studio.dabble.protomorphic.kotlin

/** A minimal indenting writer for generated Kotlin. */
class CodeWriter {
    private val out = StringBuilder()
    private var indent = 0

    fun line(text: String = "") {
        if (text.isEmpty()) out.append('\n') else out.append("  ".repeat(indent)).append(text).append('\n')
    }

    /** Writes `header` + [open] (default ` {`), the [body] indented, then `}` + [suffix]. */
    fun block(header: String, suffix: String = "", open: String = "{", body: CodeWriter.() -> Unit) {
        line("$header $open")
        indent++
        body()
        indent--
        line("}$suffix")
    }

    /** Writes [comment] as KDoc, if it is not blank. */
    fun kdoc(comment: String?) {
        val lines = comment?.trimEnd()?.lines()?.map { it.removePrefix(" ").trimEnd() } ?: return
        if (lines.all { it.isBlank() }) return
        line("/**")
        lines.forEach { line(if (it.isEmpty()) " *" else " * ${it.replace("*/", "*&#47;").replace("/*", "&#47;*")}") }
        line(" */")
    }

    override fun toString(): String = out.toString()
}
