package io.amplicode.connekt

import io.amplicode.connekt.auth.*
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.AuthExtensions
import io.amplicode.connekt.dsl.ValueDelegate
import kotlin.reflect.KProperty

class ConnektAuthExtensionsImpl(
    private val context: ConnektContext
) : AuthExtensions {

    override fun oauth(
        authorizeEndpoint: String,
        clientId: String,
        clientSecret: String?,
        scope: String,
        tokenEndpoint: String,
        redirectUri: String
    ): OAuthRunner {
        val authRunner = OAuthRunner(
            authorizeEndpoint,
            clientId,
            scope,
            tokenEndpoint,
            clientSecret,
            redirectUri,
            context
        )

        context.executionContext.registerExecutable(
            authRunner
        )

        return authRunner
    }

    override fun OAuthRunner.provideDelegate(
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<Auth> {
        return AuthValueDelegate(prop, this, context)
    }
}

private class AuthValueDelegate(
    private val prop: KProperty<*>,
    private val runner: OAuthRunner,
    private val context: ConnektContext
) : ValueDelegateBase<Auth>() {
    private val key = prop.name
    private val storeMap = context.vars

    override fun getValueImpl(
        thisRef: Any?,
        property: KProperty<*>
    ): Auth {
        var auth = storeMap.getValue<Auth>(key, prop.returnType)

        val currentTime = System.currentTimeMillis()

        auth = when {
            auth == null -> runner.authorize()

            currentTime > auth.refreshTokenExpirationTs -> runner.authorize()

            currentTime > auth.accessTokenExpirationTs -> runner.refresh(auth)

            else -> auth
        }

        storeMap.setValue(key, auth)

        return auth
    }
}
