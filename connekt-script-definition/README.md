# Connekt Script Definition

A Kotlin scripting library for defining and executing HTTP requests. This library provides a powerful DSL for creating, configuring, and executing HTTP requests in a concise and readable way.

## Overview

Connekt Script Definition is a part of the Connekt project that enables you to write HTTP request scripts using Kotlin. It provides a domain-specific language (DSL) for defining HTTP requests, handling responses, and managing state between requests.

The library is designed to make API testing, automation, and integration tasks easier by providing a clean, type-safe syntax for HTTP operations.

## Features

- **Kotlin Scripting**: Write scripts with the `.connekt.kts` extension using Kotlin's scripting capabilities
- **HTTP Methods**: Support for all standard HTTP methods (GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, TRACE)
- **Request Configuration**: 
  - Headers
  - Query parameters
  - Path parameters
  - Request body (JSON, text, form data, multipart)
  - Cookies
  - SSL/TLS configuration
- **Response Handling**: 
  - JSON parsing with JSONPath
  - Response validation
  - File downloads
- **State Management**:
  - Variables store for sharing data between requests
  - Environment configuration
- **Use Cases**: Group related requests into use cases for better organization
- **Dependency Management**: Add Maven dependencies to your scripts

## Usage Examples

### Basic GET Request

```kotlin
GET("https://api.example.com/users") {
    header("Authorization", "Bearer token123")
    queryParam("limit", 10)
}
```

### POST Request with JSON Body

```kotlin
POST("https://api.example.com/users") {
    contentType("application/json")
    body("""
        {
            "name": "John Doe",
            "email": "john@example.com"
        }
    """)
}
```

### Handling Responses

```kotlin
GET("https://api.example.com/users/1") then {
    val name = decode<String>("$.name")
    assertEquals("John Doe", name)
}
```

### Using Variables

```kotlin
val userId: String by POST("https://api.example.com/users") {
    contentType("application/json")
    body("""{"name": "John Doe"}""")
} then {
    jsonPath().read("$.id")
}

GET("https://api.example.com/users/$userId")
```

### Form Data

```kotlin
POST("https://api.example.com/form") {
    formData {
        field("name", "John Doe")
        field("email", "john@example.com")
    }
}
```

### Multipart Requests

```kotlin
POST("https://api.example.com/upload") {
    multipart {
        file("document", "file.txt", File("path/to/file.txt"))
        part("metadata") {
            body("""{"description": "My document"}""")
        }
    }
}
```

### Path Parameters

Supply values for `{placeholder}` segments in the URL:

```kotlin
GET("https://api.example.com/users/{id}/posts/{postId}") {
    pathParam("id", "user-123")
    pathParam("postId", 42)
}
```

### Authentication Helpers

`basicAuth` sets `Authorization: Basic ...`, `bearerAuth` sets `Authorization: Bearer ...`:

```kotlin
GET("https://api.example.com/profile") {
    basicAuth("username", "password")
}

GET("https://api.example.com/profile") {
    bearerAuth("my-access-token")
}
```

### SSL/TLS Configuration

Use `configureClient` to customize SSL behavior for all requests in the script:

```kotlin
// WARNING: disables all SSL verification — for local development only
configureClient {
    insecure()
}

// Trust a self-signed certificate from a PEM file
configureClient {
    addX509Certificate(File("ca.crt"))
}

// Trust certificates from a JKS or PKCS12 keystore
configureClient {
    addKeyStore(File("keystore.jks"), "password")
}
```

`configureClient` can also be called inside a single request block to limit the scope to that request:

```kotlin
GET("https://internal.example.com/api") {
    configureClient {
        addX509Certificate(File("ca.crt"))
    }
}
```

### OAuth2 Authorization Code Flow (Experimental)

Initiate the Authorization Code flow with `oauth` and use the resulting `Auth` object:

```kotlin
val auth by oauth(
    authorizeEndpoint = "https://auth.example.com/oauth/authorize",
    clientId = "my-client",
    clientSecret = "secret",
    scope = "openid profile",
    tokenEndpoint = "https://auth.example.com/oauth/token",
    redirectUri = "http://localhost:8085/callback"
)

GET("https://api.example.com/resource") {
    bearerAuth(auth.accessToken)
}
```

For Keycloak, use the `KeycloakOAuthParameters` shortcut:

```kotlin
val auth by oauth(
    KeycloakOAuthParameters(
        serverBaseUrl = "https://keycloak.example.com",
        realm = "my-realm",
        protocol = "openid-connect",
        clientId = "my-client",
        clientSecret = null,
        scope = "openid profile",
        callbackPort = 8085,
        callbackPath = "/callback"
    )
)

GET("https://api.example.com/resource") {
    bearerAuth(auth.accessToken)
}
```

### Cookies

Cookies are automatically sent from the session's cookie jar. Use `noCookies()` to opt out for a specific request:

```kotlin
GET("https://api.example.com/resource") {
    noCookies()
}
```

### Redirect Handling

By default, redirects are followed automatically. Call `noRedirect()` to receive the `3xx` response directly and inspect the `Location` header:

```kotlin
GET("https://api.example.com/old-url") {
    noRedirect()
}.then {
    println(header("Location"))
}
```

### Environment Variables

Bind values from the Connekt environment configuration (e.g. `connekt.env.json`) using property delegation. The property name is used as the lookup key:

```kotlin
val baseUrl: String by env
val port: Int by env
```

### Use Cases

Group related requests into a named use case. The return value of the block is captured and bound to the variable:

```kotlin
val petNames by useCase("Load pet names") {
    val response by GET("$baseUrl/pets")
    response.decode<List<String>>("$.name")
}
```

## Dependencies

The library depends on:
- Kotlin Scripting libraries
- OkHttp for HTTP client functionality
- JsonPath for JSON parsing
- Jackson for JSON processing
- MapDB for state storage
- Kotlinx Serialization

## License

Copyright (c) Haulmont 2024. All Rights Reserved.
Use is subject to license terms.