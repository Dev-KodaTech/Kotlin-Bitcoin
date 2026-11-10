package com.kotlinbitcoins.domain.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OutcomeTest {
    private val success: Outcome<String, Int> = Outcome.Success(2)
    private val failure: Outcome<String, Int> = Outcome.Failure("boom")

    @Test
    fun `success carries the value and failure carries the error`() {
        assertThat((success as Outcome.Success).value).isEqualTo(2)
        assertThat((failure as Outcome.Failure).error).isEqualTo("boom")
    }

    @Test
    fun `map transforms the value of a success`() {
        assertThat(success.map { it * 10 }).isEqualTo(Outcome.Success(20))
    }

    @Test
    fun `map does not run on a failure`() {
        var hasRun = false

        val result = failure.map { hasRun = true }

        assertThat(hasRun).isFalse()
        assertThat(result).isEqualTo(Outcome.Failure("boom"))
    }

    @Test
    fun `flatMap chains a success into the next outcome`() {
        assertThat(success.flatMap { Outcome.Success(it + 1) }).isEqualTo(Outcome.Success(3))
        assertThat(success.flatMap { Outcome.Failure("next") }).isEqualTo(Outcome.Failure("next"))
    }

    @Test
    fun `flatMap does not run on a failure`() {
        var hasRun = false

        val result =
            failure.flatMap {
                hasRun = true
                Outcome.Success(it)
            }

        assertThat(hasRun).isFalse()
        assertThat(result).isEqualTo(Outcome.Failure("boom"))
    }

    @Test
    fun `mapError transforms only the error`() {
        assertThat(failure.mapError { it.length }).isEqualTo(Outcome.Failure(4))
        assertThat(success.mapError { it.length }).isEqualTo(Outcome.Success(2))
    }

    @Test
    fun `fold covers both branches`() {
        val onSuccess = success.fold(onFailure = { "error $it" }, onSuccess = { "value $it" })
        val onFailure = failure.fold(onFailure = { "error $it" }, onSuccess = { "value $it" })

        assertThat(onSuccess).isEqualTo("value 2")
        assertThat(onFailure).isEqualTo("error boom")
    }
}
