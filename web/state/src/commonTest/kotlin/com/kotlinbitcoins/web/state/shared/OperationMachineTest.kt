package com.kotlinbitcoins.web.state.shared

import com.kotlinbitcoins.web.core.error.AmountInputError
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.format.AmountInputParser
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.state.support.money
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OperationMachineTest {
    private val submitted = mutableListOf<Money>()
    private var response = CompletableDeferred<Outcome<OperationError, String>>()

    private fun TestScope.machine(hasReview: Boolean = true): OperationMachine<Money, String> =
        OperationMachine(
            scope = backgroundScope,
            hasReview = hasReview,
            parse = AmountInputParser::parse,
            submit = { amount ->
                submitted += amount
                response.await()
            },
        )

    private fun TestScope.reviewing(): OperationMachine<Money, String> =
        machine().apply {
            onAmountChange("100,00")
            onReview()
            runCurrent()
        }

    @Test
    fun startsEditingEmpty() =
        runTest {
            assertEquals(OperationState.Editing(""), machine().state.value)
        }

    @Test
    fun reviewOnlyWithAValidInput() =
        runTest {
            val machine = machine()
            machine.onAmountChange("abc")
            machine.onReview()
            assertEquals(
                OperationState.Editing("abc", OperationInputError.Invalid(AmountInputError.InvalidFormat)),
                machine.state.value,
            )
            machine.onAmountChange("100,00")
            assertEquals(OperationState.Editing("100,00"), machine.state.value)
            machine.onReview()
            assertEquals(OperationState.Reviewing(money("100.00")), machine.state.value)
        }

    @Test
    fun confirmIsIgnoredWhileEditingWhenThereIsAReview() =
        runTest {
            val machine = machine()
            machine.onAmountChange("100,00")
            machine.onConfirm()
            runCurrent()
            assertEquals(OperationState.Editing("100,00"), machine.state.value)
            assertTrue(submitted.isEmpty())
        }

    @Test
    fun confirmFromEditingWithoutReview() =
        runTest {
            val machine = machine(hasReview = false)
            machine.onAmountChange("0")
            machine.onConfirm()
            assertEquals(
                OperationState.Editing("0", OperationInputError.Invalid(AmountInputError.NotPositive)),
                machine.state.value,
            )
            machine.onAmountChange("50")
            machine.onConfirm()
            assertEquals(OperationState.Submitting(money("50.00")), machine.state.value)
        }

    @Test
    fun goesToSubmittingBeforeCallingTheGateway() =
        runTest {
            val machine = reviewing()
            machine.onConfirm()
            assertEquals(OperationState.Submitting(money("100.00")), machine.state.value)
            assertTrue(submitted.isEmpty(), "the gateway is called only after the state change")
            runCurrent()
            assertEquals(listOf(money("100.00")), submitted)
        }

    @Test
    fun fiveConfirmationsSendOnce() =
        runTest {
            val machine = reviewing()
            repeat(5) { machine.onConfirm() }
            runCurrent()
            repeat(5) { machine.onConfirm() }
            runCurrent()
            assertEquals(1, submitted.size)
            response.complete(Outcome.Success("ok"))
            runCurrent()
            machine.onConfirm()
            runCurrent()
            assertEquals(1, submitted.size)
        }

    @Test
    fun succeededKeepsOnlyTheResult() =
        runTest {
            val machine = reviewing()
            machine.onConfirm()
            response.complete(Outcome.Success("purchase-1"))
            runCurrent()
            assertEquals(OperationState.Succeeded("purchase-1"), machine.state.value)
        }

    @Test
    fun retryAfterAnUnavailableQuoteOrServiceGoesBackToReviewWithoutSending() =
        runTest {
            listOf(ApiError.QuoteUnavailable, ApiError.ServiceUnavailable).forEach { error ->
                submitted.clear()
                response = CompletableDeferred()
                val machine = reviewing()
                machine.onConfirm()
                response.complete(Outcome.Failure(OperationError.Rejected(error)))
                runCurrent()
                assertEquals(OperationState.Rejected(money("100.00"), error), machine.state.value)

                machine.onRetry()
                runCurrent()
                assertEquals(OperationState.Reviewing(money("100.00")), machine.state.value)
                assertEquals(1, submitted.size, "retry needs a new explicit confirmation")
            }
        }

    @Test
    fun retryIsNotOfferedForOtherRejections() =
        runTest {
            val machine = reviewing()
            machine.onConfirm()
            response.complete(Outcome.Failure(OperationError.Rejected(ApiError.InsufficientBalance)))
            runCurrent()
            machine.onRetry()
            assertEquals(OperationState.Rejected(money("100.00"), ApiError.InsufficientBalance), machine.state.value)
            machine.onEdit()
            assertEquals(OperationState.Editing("100,00"), machine.state.value)
        }

    @Test
    fun invalidAmountFromTheApiGoesBackToEditingWithTheFieldError() =
        runTest {
            val machine = reviewing()
            machine.onConfirm()
            response.complete(Outcome.Failure(OperationError.Rejected(ApiError.InvalidAmount)))
            runCurrent()
            assertEquals(OperationState.Editing("100,00", OperationInputError.RefusedByApi), machine.state.value)
        }

    @Test
    fun uncertainDoesNotAcceptRetry() =
        runTest {
            val machine = reviewing()
            machine.onConfirm()
            response.complete(Outcome.Failure(OperationError.Uncertain))
            runCurrent()
            assertIs<OperationState.Uncertain>(machine.state.value)
            machine.onRetry()
            machine.onConfirm()
            machine.onEdit()
            runCurrent()
            assertIs<OperationState.Uncertain>(machine.state.value)
            assertEquals(1, submitted.size)
        }

    @Test
    fun newOperationGoesBackToAnEmptyForm() =
        runTest {
            val machine = reviewing()
            machine.onConfirm()
            response.complete(Outcome.Success("ok"))
            runCurrent()
            machine.onNewOperation()
            assertEquals(OperationState.Editing(""), machine.state.value)
        }

    @Test
    fun editFromReviewKeepsTheInput() =
        runTest {
            val machine = reviewing()
            machine.onEdit()
            assertEquals(OperationState.Editing("100,00"), machine.state.value)
        }
}
