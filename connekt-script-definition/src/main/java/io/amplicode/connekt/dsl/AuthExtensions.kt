package io.amplicode.connekt.dsl

import io.amplicode.connekt.RequestBuilderCall
import io.amplicode.connekt.auth.Auth
import io.amplicode.connekt.auth.OAuthRunner
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KProperty

/**
 * Configuration parameters for authenticating against a Keycloak server using OAuth 2.0.
 *
 * These parameters are used by [AuthExtensions.oauth] to construct the Keycloak-specific
 * authorization and token endpoint URLs automatically.
 *
 * @property serverBaseUrl Base URL of the Keycloak server, e.g. `https://keycloak.example.com`.
 * @property realm The Keycloak realm to authenticate against, e.g. `my-realm`.
 * @property protocol The Keycloak protocol to use for the OAuth flow, typically `openid-connect`.
 * @property clientId The OAuth client identifier registered in the Keycloak realm.
 * @property clientSecret The OAuth client secret associated with [clientId], or `null` for public clients.
 * @property scope Space-separated list of OAuth scopes to request, e.g. `openid profile email`.
 * @property callbackPort Local port on which the temporary callback HTTP server will listen for the
 *   authorization code redirect from the Keycloak server.
 * @property callbackPath URL path appended to the local callback server address, e.g. `/callback`.
 *   Combined with [callbackPort] to form the redirect URI: `http://localhost:<callbackPort><callbackPath>`.
 */
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

/**
 * Provides OAuth 2.0 authorization support within a Connekt request builder context.
 *
 * Implementations of this interface expose the [oauth] function for initiating the OAuth
 * authorization code flow, and the [OAuthRunner.provideDelegate] operator that enables
 * property delegation syntax (`val auth by oauth(...)`).
 */
interface AuthExtensions {

    /**
     * Initiates an OAuth 2.0 authorization code flow and returns an [OAuthRunner] that manages
     * the full flow: opening the browser for user authorization, starting a local callback server
     * to capture the authorization code, and exchanging it for access and refresh tokens.
     *
     * Use with property delegation via [OAuthRunner.provideDelegate]:
     * ```kotlin
     * val auth by oauth(
     *     authorizeEndpoint = "https://auth.example.com/oauth/authorize",
     *     clientId = "my-client",
     *     clientSecret = "secret",
     *     scope = "openid profile",
     *     tokenEndpoint = "https://auth.example.com/oauth/token",
     *     redirectUri = "http://localhost:8080/callback"
     * )
     * ```
     *
     * @param authorizeEndpoint URL of the authorization endpoint where the user is redirected to log in.
     * @param clientId The OAuth client identifier.
     * @param clientSecret The OAuth client secret, or `null` for public clients.
     * @param scope Space-separated list of OAuth scopes to request.
     * @param tokenEndpoint URL of the token endpoint used to exchange the authorization code for tokens.
     * @param redirectUri The redirect URI to which the authorization server sends the authorization code.
     * @return An [OAuthRunner] that executes the flow when used as a property delegate.
     */
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

/**
 * Initiates an OAuth 2.0 authorization code flow using Keycloak-specific endpoint URLs constructed
 * from the provided [parameters].
 *
 * The authorization and token endpoint URLs are derived automatically:
 * - Authorize endpoint: `<serverBaseUrl>/realms/<realm>/protocol/<protocol>/auth`
 * - Token endpoint:     `<serverBaseUrl>/realms/<realm>/protocol/<protocol>/token`
 * - Redirect URI:       `http://localhost:<callbackPort><callbackPath>`
 *
 * This is a convenience wrapper around [AuthExtensions.oauth] that removes the need to construct
 * Keycloak URLs manually.
 *
 * Example usage:
 * ```kotlin
 * val auth by oauth(
 *     KeycloakOAuthParameters(
 *         serverBaseUrl = "https://keycloak.example.com",
 *         realm = "my-realm",
 *         protocol = "openid-connect",
 *         clientId = "my-client",
 *         clientSecret = "secret",
 *         scope = "openid profile",
 *         callbackPort = 8080,
 *         callbackPath = "/callback"
 *     )
 * )
 * ```
 *
 * @param parameters The [KeycloakOAuthParameters] containing all Keycloak connection details.
 * @return An [OAuthRunner] that executes the authorization code flow when used as a property delegate.
 */
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
