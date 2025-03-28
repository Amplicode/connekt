package io.amplicode.connekt.dsl

@ConnektDsl
class FormDataBodyBuilder {
    private val fields = mutableListOf<Pair<String, String>>()

    fun field(name: String, value: Any) {
        fields.add(name to value.toString())
    }

    internal fun build(): FormDataBody = FormDataBody(fields)
}