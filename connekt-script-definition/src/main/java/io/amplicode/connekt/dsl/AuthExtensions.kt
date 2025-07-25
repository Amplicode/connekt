package io.amplicode.connekt.dsl

import io.amplicode.connekt.RequestBuilderCall
import io.amplicode.connekt.auth.Auth
import io.amplicode.connekt.auth.OAuthRunner
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KProperty

data class KeycloakOAuthParameters(
    val serverBaseUrl: String,
    val realm: String,
    val protocol: String,
    val clientId: String,
    val clientSecret: String?,
    val scope: String,
    val callbackPort: Int,
    val callbackPath: String
)

interface AuthExtensions {

    @RequestBuilderCall
    @ApiStatus.Experimental
    fun oauth(
        authorizeEndpoint: String,
        clientId: String,
        clientSecret: String?,
        scope: String,
        tokenEndpoint: String,
        redirectUri: String
    ): OAuthRunner

    operator fun OAuthRunner.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<Auth>
}

@RequestBuilderCall
@ApiStatus.Experimental
fun AuthExtensions.oauth(parameters: KeycloakOAuthParameters): OAuthRunner {
    val authorizeEndpoint: String = with(parameters) {
        "$serverBaseUrl/realms/$realm/protocol/$protocol/auth"
    }

    val tokenEndpoint: String = with(parameters) {
        "$serverBaseUrl/realms/$realm/protocol/$protocol/token"
    }

    return oauth(
        authorizeEndpoint,
        parameters.clientId,
        parameters.clientSecret,
        parameters.scope,
        tokenEndpoint,
        "http://localhost:${parameters.callbackPort}${parameters.callbackPath}"
    )
}