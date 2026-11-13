package com.kotlinbitcoins.web.core.support

import com.kotlinbitcoins.web.core.error.Outcome
import kotlin.test.fail

fun <E, T> Outcome<E, T>.successValue(): T =
    when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("Expected Success, got Failure($error)")
    }

fun <E, T> Outcome<E, T>.failureError(): E =
    when (this) {
        is Outcome.Success -> fail("Expected Failure, got Success($value)")
        is Outcome.Failure -> error
    }
