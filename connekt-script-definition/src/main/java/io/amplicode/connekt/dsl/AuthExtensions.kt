package io.amplicode.connekt.dsl

import io.amplicode.connekt.RequestBuilderCall
import io.amplicode.connekt.auth.Auth
import io.amplicode.connekt.auth.AuthRunner
import io.amplicode.connekt.auth.KeycloakOAuthParameters
import kotlin.reflect.KProperty

interface AuthExtensions {

    @RequestBuilderCall
    fun oauth(
        authorizeEndpoint: String,
        clientId: String,
        clientSecret: String,
        scope: String,
        tokenEndpoint: String,
        redirectUri: String
    ): AuthRunner

    @RequestBuilderCall
    fun oauth(oAuthParameters: KeycloakOAuthParameters): AuthRunner

    operator fun AuthRunner.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<Auth>
}
