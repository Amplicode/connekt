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
        clientSecret: String,
        scope: String,
        tokenEndpoint: String,
        redirectUri: String
    ): AuthRunner {
        val authRunner = GoogleAuthRunner(
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

    override fun oauth(oAuthParameters: KeycloakOAuthParameters): AuthRunner {
        val authRunner = KeycloakAuthRunner(context, oAuthParameters)
        context.executionContext.registerExecutable(
            authRunner
        )
        return authRunner
    }

    override fun AuthRunner.provideDelegate(
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<Auth> {
        val runner = this

        return object : ValueDelegateBase<Auth>() {
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
    }
}