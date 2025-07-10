package io.amplicode.connekt

import com.jayway.jsonpath.ReadContext
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.JsonPathExtensionsProvider
import io.amplicode.connekt.dsl.RequestBuilder
import io.amplicode.connekt.dsl.UseCaseBuilder
import okhttp3.Response
import kotlin.reflect.KProperty

internal class UseCaseBuilderImpl(
    private val context: ConnektContext,
    private val eachRequestExecutionStrategy: RequestExecutionStrategy,
    jsonPathExtensionsProvider: JsonPathExtensionsProvider = JsonExtensionsProviderImpl(context)
) : UseCaseBuilder,
    JsonPathExtensionsProvider by jsonPathExtensionsProvider {

    override operator fun <T> T.provideDelegate(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): UseCaseValueDelegate<T> {
        return UseCaseValueDelegate(this)
    }

    override fun Response.jsonPath(): ReadContext = context.jsonContext.getReadContext(this)

    override fun request(
        method: String,
        path: String,
        configure: RequestBuilder.() -> Unit
    ): Response {
        val requestBuilder = RequestBuilder(method, path, context)
            .apply(configure)
        return eachRequestExecutionStrategy.executeRequest(requestBuilder)
    }

    override fun keycloakOAuth(): ExecutableWithResult<KeycloakOAuth> {
        TODO("Not yet implemented")
    }
}