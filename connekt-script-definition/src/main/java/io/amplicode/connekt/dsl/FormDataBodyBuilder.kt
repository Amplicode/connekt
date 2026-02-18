package io.amplicode.connekt.dsl

/**
 * Builder for constructing `application/x-www-form-urlencoded` request bodies.
 *
 * Use this builder inside a `formData {}` block to add key-value fields that will be
 * URL-encoded and sent as the request body.
 *
 * Example usage:
 * ```
 * formData {
 *     field("username", "alice")
 *     field("password", "secret")
 * }
 * ```
 */
@ConnektDsl
class FormDataBodyBuilder {
    private val fields = mutableListOf<Pair<String, String>>()

    /**
     * Adds a form field with the given [name] and [value] to the request body.
     *
     * The [value] is converted to a string using [Any.toString] before being added.
     *
     * @param name The name of the form field.
     * @param value The value of the form field.
     */
    fun field(name: String, value: Any) {
        fields.add(name to value.toString())
    }

    internal fun build(): FormDataBody = FormDataBody(fields)
}
