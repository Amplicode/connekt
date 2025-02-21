package io.amplicode.connekt

class MissingPathParameterException(val parameterName: String) :
    IllegalArgumentException("Missing path parameter: $parameterName")