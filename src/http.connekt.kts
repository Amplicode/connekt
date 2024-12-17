import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.util.*

val baseUrl: String by env
val foo: Int by env
val authorize: Boolean by env;

val token by POST("https://examples.http-client.intellij.net/body-echo") {
    contentType("application/json")

    body(
        """
        {
          "token": "my-secret-token"
        }
    """.trimIndent()
    )

    noCookies()
} then {
    jsonPath().readString("token")
}


GET("https://examples.http-client.intellij.net/ip") {
    accept("application/json")
}

// ### GET request with parameter
// GET https://examples.http-client.intellij.net/get?show_env=1
// Accept: application/json

GET("https://examples.http-client.intellij.net/get?show_env=1") {
    accept("application/json")
}

GET("https://examples.http-client.intellij.net/get") {
    accept("application/json")
    queryParam("show_env", 1)
}

fun someLogic(): Boolean {
    return true
}

GET("https://examples.http-client.intellij.net/get") {
    accept("application/json")

    if (someLogic()) {
        queryParam("show_env", 1)
    }
}

// ### GET request with disabled redirects
// # @no-redirect
// GET https://examples.http-client.intellij.net/status/301

GET("https://examples.http-client.intellij.net/status/301") {
    noRedirect()
}

// ### Send POST request with body as parameters
// POST https://examples.http-client.intellij.net/post
// Content-Type: application/x-www-form-urlencoded
//
// id = 999 &
// value = content &
// fact = IntelliJ %+ HTTP Client %= <3

POST("https://examples.http-client.intellij.net/post") {
    formData {
        field("id", 999)
        field("value", "content")
        field("fact", "IntelliJ + Connekt %= <3")
    }
} then {
    assertThat(code).isEqualTo(400)
}


// ### Send request with dynamic variables in request's body
// POST https://examples.http-client.intellij.net/post
// Content-Type: application/json
//
// {
//   "id": {{$random.uuid}},
//   "price": {{$random.integer()}},
//   "ts": {{$timestamp}},
//   "value": "content"
// }

POST("https://examples.http-client.intellij.net/post") {
    contentType("application/json")
    body(
        """
        {
           "id": "${UUID.randomUUID()}",
           "price": ${Random.nextInt()},
           "ts": ${Random.nextLong()},
           "value": "content"
        }
    """.trimIndent()
    )
}

// ### Send a form with the text and file fields
// POST https://examples.http-client.intellij.net/post
// Content-Type: multipart/form-data; boundary=WebAppBoundary
//
// --WebAppBoundary
// Content-Disposition: form-data; name="element-name"
// Content-Type: text/plain
//
// Name
// --WebAppBoundary
// Content-Disposition: form-data; name="data"; filename="data.json"
// Content-Type: application/json
//
// < ./request-form-data.json
// --WebAppBoundary--

POST("https://examples.http-client.intellij.net/post") {
    multipart {
        part(name = "element-name", contentType = "text/plain") {
            body("Name")
        }

        file("data", "data.json", File("request-form-data.json"))
    }
}

// misc

GET("https://jsonplaceholder.typicode.com/auth") {
    bearerAuth(token)
    contentType("application/json")
    queryParam("postId", "1")
} then {
    val jsonPath = jsonPath()

    assertThat(code).isEqualTo(200)

    assertThat(jsonPath.read<Int>("size()")).isGreaterThan(0)
    assertThat(jsonPath.read<Int>("[0].postId")).isEqualTo(1)
}

GET("https://jsonplaceholder.typicode.com/comments") {
    contentType("application/json")
    queryParam("postId", "1")
} then {
    assertThat(code).isEqualTo(200)
}
