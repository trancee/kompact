package ch.trancee.kompact.processor.registry

import java.util.ArrayDeque
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

internal object JsonDuplicateKeyValidator {
    fun validate(content: String) {
        val containers = ArrayDeque<Container>()
        var index = 0
        while (index < content.length) {
            when (content[index]) {
                '{' -> containers.addLast(Container.Object())
                '[' -> containers.addLast(Container.Array)
                '}' -> require(containers.pollLast() is Container.Object) { "invalid JSON" }
                ']' -> require(containers.pollLast() === Container.Array) { "invalid JSON" }
                ',' -> (containers.lastOrNull() as? Container.Object)?.expectsKey = true
                '"' -> {
                    val end = stringEnd(content, index)
                    val objectContainer = containers.lastOrNull() as? Container.Object
                    if (objectContainer?.expectsKey == true) {
                        val key =
                            Json.parseToJsonElement(content.substring(index, end + 1))
                                .jsonPrimitive
                                .content
                        require(objectContainer.keys.add(key)) {
                            "duplicate JSON object key '$key'"
                        }
                        objectContainer.expectsKey = false
                    }
                    index = end
                }
            }
            index++
        }
        require(containers.isEmpty()) { "invalid JSON" }
    }

    private fun stringEnd(content: String, start: Int): Int {
        var escaped = false
        var index = start + 1
        while (index < content.length) {
            val character = content[index]
            if (!escaped && character == '"') return index
            escaped = !escaped && character == '\\'
            if (character != '\\') escaped = false
            index++
        }
        throw IllegalArgumentException("unterminated JSON string")
    }

    private sealed interface Container {
        data class Object(
            val keys: MutableSet<String> = mutableSetOf(),
            var expectsKey: Boolean = true,
        ) : Container

        data object Array : Container
    }
}
