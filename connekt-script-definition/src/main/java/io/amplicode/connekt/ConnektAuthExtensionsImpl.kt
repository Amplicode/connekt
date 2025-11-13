package io.amplicode.connekt

import io.amplicode.connekt.auth.Auth
import io.amplicode.connekt.auth.OAuthRunner
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.execution.DeclarationCoordinates
import io.amplicode.connekt.dsl.AuthExtensions
import io.amplicode.connekt.dsl.ValueDelegate
import kotlin.reflect.KProperty

class ConnektAuthExtensionsImpl(private val context: ConnektContext) : AuthExtensions {

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

class AuthValueDelegate(
    private val prop: KProperty<*>,
    private val runner: OAuthRunner,
    context: ConnektContext
) : ValueDelegateBase<Auth>() {
    private val key = prop.name
    private val storeMap = context.variablesStore

    init {
        runner.addListener(object : OAuthRunner.Listener {
            override fun onAuthorized(auth: Auth) {
                // Store new value from observed runner
                storeMap.setValue(key, auth)
            }
        })
        runner.storedAuthProvider = {
            storeMap.getValue<Auth>(key, prop.returnType)
        }
    }

    override fun getValueImpl(
        thisRef: Any?,
        property: KProperty<*>
    ): Auth = runner.getAuth()
}
