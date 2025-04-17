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
GET("https://api.example.com/users/1").then {
    val userData = jsonPath().json<Map<String, Any>>()
    assertEquals("John Doe", userData["name"])
}
```

### Using Variables

```kotlin
val userId by variable<String>()

POST("https://api.example.com/users") {
    contentType("application/json")
    body("""{"name": "John Doe"}""")
}.then {
    userId.set(jsonPath().read("$.id"))
}

GET("https://api.example.com/users/${userId.get()}")
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