fun assertContains(body: String, substring: String) {
    assert(body.contains(substring)) { "Expected '$substring' in response body, got: $body" }
}
