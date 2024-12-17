/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "connekt.kts",
    compilationConfiguration = ConnektConfiguration::class
)
abstract class Connekt