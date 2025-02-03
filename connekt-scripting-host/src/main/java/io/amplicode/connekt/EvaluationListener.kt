/*
 * Copyright (c) Haulmont 2025. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

fun interface EvaluationListener {

    fun onEvaluation()

    companion object {
        val NOOP: EvaluationListener = object : EvaluationListener {
            override fun onEvaluation() {
                // Do nothing
            }
        }
    }
}