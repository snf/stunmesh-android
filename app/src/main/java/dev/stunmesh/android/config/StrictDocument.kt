package dev.stunmesh.android.config

import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag
import java.io.StringReader
import java.util.Locale

/** One bounded parser for JSON/YAML. Never construct user-selected classes or
 * include parser excerpts in errors. Anchors, aliases, nulls and duplicate keys
 * are rejected before producing a configuration object. */
object StrictDocument {
    const val MAX_BYTES = 256 * 1024
    fun parse(text: String): JSONObject = try {
        require(text.length <= MAX_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 0
            nestingDepthLimit = 16
            codePointLimit = MAX_BYTES
        }
        val node = Yaml(SafeConstructor(options)).compose(StringReader(text))
            ?: throw IllegalArgumentException()
        convert(node, 0) as? JSONObject ?: throw IllegalArgumentException()
    } catch (_: Exception) { throw IllegalArgumentException("Invalid or oversized configuration") }

    private fun convert(node: Node, depth: Int): Any {
        require(depth <= 16 && node.anchor == null)
        return when (node) {
            is MappingNode -> {
                require(node.tag == Tag.MAP && node.value.size <= 256)
                val seen = mutableSetOf<String>()
                JSONObject().apply {
                    node.value.forEach { entry ->
                        val key = entry.keyNode as? ScalarNode ?: throw IllegalArgumentException()
                        require(key.tag == Tag.STR && key.anchor == null && key.value.length <= 128)
                        require(seen.add(key.value.lowercase(Locale.ROOT)))
                        put(key.value, convert(entry.valueNode, depth + 1))
                    }
                }
            }
            is SequenceNode -> {
                require(node.tag == Tag.SEQ && node.value.size <= 256)
                JSONArray().apply { node.value.forEach { put(convert(it, depth + 1)) } }
            }
            is ScalarNode -> when (node.tag) {
                Tag.STR -> node.value.also { require(it.length <= 4096) }
                Tag.INT -> node.value.toInt() // no lossy narrowing/coercion
                Tag.BOOL -> when (node.value) { "true" -> true; "false" -> false; else -> throw IllegalArgumentException() }
                else -> throw IllegalArgumentException()
            }
            else -> throw IllegalArgumentException()
        }
    }
}

internal fun JSONObject.fields(vararg allowed: String) {
    require(keys().asSequence().all { it in allowed }) { "Unsupported configuration field" }
}
internal fun JSONObject.text(name: String, fallback: String = ""): String =
    if (!has(name)) fallback else get(name) as? String ?: throw IllegalArgumentException("Invalid text field")
internal fun JSONObject.number(name: String, fallback: Int): Int =
    if (!has(name)) fallback else get(name) as? Int ?: throw IllegalArgumentException("Invalid number field")
internal fun JSONObject.obj(name: String): JSONObject =
    if (!has(name)) JSONObject() else get(name) as? JSONObject ?: throw IllegalArgumentException("Invalid object field")
internal fun JSONObject.array(name: String): JSONArray =
    if (!has(name)) JSONArray() else get(name) as? JSONArray ?: throw IllegalArgumentException("Invalid list field")
internal fun JSONObject.strings(name: String): List<String> = array(name).let { array ->
    (0 until array.length()).map { array.get(it) as? String ?: throw IllegalArgumentException("Invalid list value") }
}
