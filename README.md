# Connekt

Connekt is a powerful Kotlin-based HTTP scripting tool designed for API testing, automation, and integration tasks. It provides a clean, type-safe syntax for defining and executing HTTP operations.

## Overview

Connekt consists of two main modules:

1. **Connekt Script Definition**: A Kotlin scripting library that provides a domain-specific language (DSL) for defining HTTP requests, handling responses, and managing state between requests.

2. **Connekt Scripting Host**: A command-line tool and runtime environment for executing Connekt scripts. It can be run directly or as a Docker container.

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

## Installation

### Using Docker

The easiest way to use Connekt is via Docker:

```bash
docker pull ghcr.io/amplicode/connekt:latest
```

### Building from Source

1. Clone the repository
2. Build the project:

```bash
./gradlew build
```

3. Run the application:

```bash
./gradlew :connekt-scripting-host:run --args="path/to/your/script.connekt.kts"
```

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

For more examples and detailed documentation, see the [Connekt Script Definition README](connekt-script-definition/README.md).

## Project Structure

- **connekt-script-definition**: Core library that defines the DSL for HTTP requests
- **connekt-scripting-host**: Command-line tool and runtime environment for executing scripts

## License

Copyright (c) Haulmont 2024. All Rights Reserved.
Use is subject to license terms.