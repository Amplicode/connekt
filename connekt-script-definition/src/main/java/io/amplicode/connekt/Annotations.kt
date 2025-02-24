/*
 * Copyright (c) Haulmont 2025. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

/**
 * Used to mark request builder functions to help tooling to detect such functions
 */
@Target(AnnotationTarget.FUNCTION)
annotation class RequestBuilderCall

/**
 * Contains request meta-data
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Request(
    val method: String
)

/**
 * Used to mark string arguments that contain request path
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RequestPath

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class HeaderName

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class HeaderValue(
    val ofHeader: String = ""
)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Header

/**
 * Marks elements with HTTP body
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Body