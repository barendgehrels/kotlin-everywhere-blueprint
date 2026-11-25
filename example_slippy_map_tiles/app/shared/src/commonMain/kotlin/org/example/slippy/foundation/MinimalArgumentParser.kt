package org.example.slippy.foundation

/**
 * Startup options as `-name value` or `--name value` (desktop, iOS, and the web's query).
 * Other arguments, such as the ones a platform adds itself, are ignored.
 */
class MinimalArgumentParser(arguments: List<String>) {
    private val values: Map<String, String> = buildMap {
        var index = 0
        while (index < arguments.size - 1) {
            val argument = arguments[index]
            if (argument.startsWith("-") && argument.trimStart('-').isNotEmpty()) {
                put(argument.trimStart('-'), arguments[index + 1])
                index += 2
            } else {
                index++
            }
        }
    }

    fun value(name: String): String? = values[name]
}
