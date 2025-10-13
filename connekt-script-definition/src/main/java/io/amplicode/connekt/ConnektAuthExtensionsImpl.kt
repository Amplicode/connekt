package io.amplicode.connekt

import io.amplicode.connekt.auth.Auth
import io.amplicode.connekt.auth.OAuthRunner
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.execution.DeclarationCoordinates
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
            authRunner,
            null
        )

        return authRunner
    }

    override fun OAuthRunner.provideDelegate(
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<Auth> {
        val oAuthRunner = this
        val coordinates = DeclarationCoordinates(prop.name)
        context.executionContext.addCoordinatesForExecutable(coordinates, oAuthRunner)
        return AuthValueDelegate(prop, this, context)
    }
}

private class AuthValueDelegate(
    private val prop: KProperty<*>,
    private val runner: OAuthRunner,
    context: ConnektContext
) : ValueDelegateBase<Auth>() {
    private val key = prop.name
    private val storeMap = context.vars

    init {
        // The OAuth declaration might be called outside the delegate context.
        // So this delegate needs to be able to know about such events
        // to update auth state to the actual one.
        runner.addListener(object : OAuthRunner.Listener {
            override fun onResult(auth: Auth) {
                storeMap.setValue(key, auth)
            }

            override fun onWaitAuthCode(authUrl: String) {
                // do nothing
            }
        })
    }

    override fun getValueImpl(
        thisRef: Any?,
        property: KProperty<*>
    ): Auth {
        val auth = storeMap.getValue<Auth>(key, prop.returnType)
        val currentTime = System.currentTimeMillis()
        return when {
            auth == null -> runner.authorize()
            currentTime > auth.refreshTokenExpirationTs -> runner.authorize()
            currentTime > auth.accessTokenExpirationTs -> runner.refresh(auth)
            else -> auth
        }
    }
}
