package com.kotlinbitcoins.web.state.purchase

import com.kotlinbitcoins.web.core.error.AmountInputError
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.PurchaseResult
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.shared.OperationInputError
import com.kotlinbitcoins.web.state.shared.OperationState
import com.kotlinbitcoins.web.state.shared.QuoteState
import com.kotlinbitcoins.web.state.support.FakeAnnouncer
import com.kotlinbitcoins.web.state.support.FakeAnnouncer.Announcement.Assertive
import com.kotlinbitcoins.web.state.support.FakeAnnouncer.Announcement.Polite
import com.kotlinbitcoins.web.state.support.FakeDashboardGateways
import com.kotlinbitcoins.web.state.support.FakePageVisibility
import com.kotlinbitcoins.web.state.support.money
import com.kotlinbitcoins.web.state.support.purchaseResult
import com.kotlinbitcoins.web.state.support.quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseViewModelTest {
    private val gateways =
        FakeDashboardGateways().apply {
            balanceResult = Outcome.Success(money("1000.00"))
            quoteResult = Outcome.Success(quote(sell = "400000.00", at = FIRST_INSTANT))
        }
    private val visibility = FakePageVisibility()
    private val announcer = FakeAnnouncer()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): PurchaseViewModel =
        PurchaseViewModel(
            accountGateway = gateways,
            quoteGateway = gateways,
            investmentGateway = gateways,
            pageVisibility = visibility,
            announcer = announcer,
            texts = { TEXTS },
            scope = backgroundScope,
        ).also { runCurrent() }

    private fun TestScope.review(
        viewModel: PurchaseViewModel,
        input: String,
    ) {
        viewModel.onAmountChange(input)
        viewModel.onReview()
        runCurrent()
    }

    private fun TestScope.confirm(viewModel: PurchaseViewModel) {
        viewModel.onConfirm()
        runCurrent()
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun startsEditingWithTheBalanceAndWithoutReadingTheQuote() =
        runTest {
            val viewModel = viewModel()
            advance(LONG_WAIT_MS)

            val state = viewModel.uiState.value
            assertEquals(OperationState.Editing(""), state.operation)
            assertEquals(Loadable.Loaded(money("1000.00")), state.balance)
            assertNull(state.estimate)
            assertEquals(0, gateways.quoteCalls)
        }

    @Test
    fun us45InvalidAmountsDoNotAdvanceAndSendNothing() =
        runTest {
            val cases =
                mapOf(
                    "" to AmountInputError.Empty,
                    "0" to AmountInputError.NotPositive,
                    "-5" to AmountInputError.InvalidFormat,
                    "abc" to AmountInputError.InvalidFormat,
                    "1,234" to AmountInputError.TooManyDecimals,
                )
            val viewModel = viewModel()
            cases.forEach { (input, reason) ->
                review(viewModel, input)
                viewModel.onConfirm()
                runCurrent()

                assertEquals(
                    OperationState.Editing(input, OperationInputError.Invalid(reason)),
                    viewModel.uiState.value.operation,
                    input,
                )
            }
            assertTrue(gateways.purchases.isEmpty())
            assertEquals(0, gateways.quoteCalls)
        }

    @Test
    fun everyReviewAttemptChangesTheAttemptSoTheFieldTakesTheFocusAgain() =
        runTest {
            val viewModel = viewModel()
            review(viewModel, "")
            val first = viewModel.uiState.value.attempt

            review(viewModel, "")

            assertEquals(first + 1, viewModel.uiState.value.attempt)
        }

    @Test
    fun us41TheReviewShowsTheBalanceTheEstimateAndTheQuoteWithItsInstant() =
        runTest {
            val viewModel = viewModel()

            review(viewModel, "400,00")

            val state = viewModel.uiState.value
            assertEquals(OperationState.Reviewing(money("400.00")), state.operation)
            assertEquals(Loadable.Loaded(money("1000.00")), state.balance)
            assertEquals(QuoteState.Available(quote(sell = "400000.00", at = FIRST_INSTANT)), state.quote)
            val estimate = assertNotNull(state.estimate)
            assertEquals("0,00100000 BTC", estimate.btc.format())
            assertEquals(quote(sell = "400000.00", at = FIRST_INSTANT), estimate.quote)
            assertEquals(false, estimate.belowOneSatoshi)
            assertTrue(gateways.purchases.isEmpty())
        }

    @Test
    fun us42ANewQuoteInTheReviewRecalculatesTheEstimateAndItsInstant() =
        runTest {
            val viewModel = viewModel()
            review(viewModel, "400,00")

            gateways.quoteResult = Outcome.Success(quote(sell = "500000.00", at = SECOND_INSTANT))
            advance(REFRESH_MS)

            val estimate = assertNotNull(viewModel.uiState.value.estimate)
            assertEquals("0,00080000 BTC", estimate.btc.format())
            assertEquals(quote(sell = "500000.00", at = SECOND_INSTANT).quotedAt, estimate.quote.quotedAt)
        }

    @Test
    fun theQuoteRefreshesOnlyWhileReviewing() =
        runTest {
            val viewModel = viewModel()
            review(viewModel, "400,00")
            advance(REFRESH_MS)
            assertEquals(2, gateways.quoteCalls)

            viewModel.onEdit()
            runCurrent()
            advance(LONG_WAIT_MS)
            assertEquals(2, gateways.quoteCalls)

            viewModel.onReview()
            runCurrent()
            assertEquals(3, gateways.quoteCalls)

            confirm(viewModel)
            advance(LONG_WAIT_MS)
            assertEquals(3, gateways.quoteCalls)
        }

    @Test
    fun aHiddenPageOrThePauseStopsTheRefreshOfTheReview() =
        runTest {
            val viewModel = viewModel()
            review(viewModel, "400,00")

            visibility.isVisible.value = false
            advance(LONG_WAIT_MS)
            assertEquals(1, gateways.quoteCalls)

            visibility.isVisible.value = true
            runCurrent()
            assertEquals(2, gateways.quoteCalls)

            viewModel.onToggleAutoRefresh()
            runCurrent()
            advance(LONG_WAIT_MS)
            assertEquals(2, gateways.quoteCalls)
            assertTrue(viewModel.uiState.value.isAutoRefreshPaused)
            assertEquals(
                listOf<FakeAnnouncer.Announcement>(Polite("Atualização automática pausada")),
                announcer.announcements,
            )
        }

    @Test
    fun us46BelowOneSatoshiWarnsAndKeepsTheConfirmation() =
        runTest {
            // R$ 0,01 at R$ 2.000.000,00 is half a satoshi (at R$ 400.000,00 it would still buy 2 satoshis).
            gateways.quoteResult = Outcome.Success(quote(sell = "2000000.00", at = FIRST_INSTANT))
            gateways.purchaseResult = Outcome.Failure(OperationError.Rejected(ApiError.InvalidAmount))
            val viewModel = viewModel()

            review(viewModel, "0,01")

            assertTrue(assertNotNull(viewModel.uiState.value.estimate).belowOneSatoshi)
            confirm(viewModel)
            assertEquals(listOf("0.01"), gateways.purchases.map { it.toApiString() })
            assertEquals(
                OperationState.Editing("0,01", OperationInputError.RefusedByApi),
                viewModel.uiState.value.operation,
            )
        }

    @Test
    fun us43TheResultShowsOnlyWhatTheApiAnsweredAndIsAnnounced() =
        runTest {
            gateways.purchaseResult = Outcome.Success(purchaseResult(balance = "600.00"))
            val viewModel = viewModel()
            review(viewModel, "400,00")

            confirm(viewModel)

            val state = viewModel.uiState.value
            val result = assertIs<OperationState.Succeeded<PurchaseResult>>(state.operation).result
            assertEquals(purchaseResult(balance = "600.00"), result)
            assertNull(state.estimate)
            assertEquals(Loadable.Loaded(money("600.00")), state.balance)
            assertEquals(listOf("400.00"), gateways.purchases.map { it.toApiString() })
            assertEquals(listOf<FakeAnnouncer.Announcement>(Assertive("Compra realizada")), announcer.announcements)
        }

    @Test
    fun us44InsufficientBalanceReloadsTheBalance() =
        runTest {
            gateways.balanceResult = Outcome.Success(money("100.00"))
            gateways.purchaseResult = Outcome.Failure(OperationError.Rejected(ApiError.InsufficientBalance))
            val viewModel = viewModel()
            review(viewModel, "100,01")
            gateways.balanceResult = Outcome.Success(money("90.00"))

            confirm(viewModel)

            val state = viewModel.uiState.value
            assertEquals(OperationState.Rejected(money("100.01"), ApiError.InsufficientBalance), state.operation)
            assertEquals(Loadable.Loaded(money("90.00")), state.balance)
            assertEquals(2, gateways.balanceCalls)
            assertTrue(announcer.announcements.isEmpty())
        }

    @Test
    fun anUnavailableQuoteInTheReviewHasNoEstimateAndKeepsTheConfirmation() =
        runTest {
            gateways.quoteResult = Outcome.Failure(ApiError.QuoteUnavailable)
            val viewModel = viewModel()

            review(viewModel, "400,00")

            assertEquals(QuoteState.Unavailable, viewModel.uiState.value.quote)
            assertNull(viewModel.uiState.value.estimate)
            confirm(viewModel)
            assertEquals(1, gateways.purchases.size)
        }

    @Test
    fun us47QuoteUnavailableOnConfirmationOffersANewReviewWithoutSending() =
        runTest {
            gateways.purchaseResult = Outcome.Failure(OperationError.Rejected(ApiError.QuoteUnavailable))
            val viewModel = viewModel()
            review(viewModel, "400,00")
            confirm(viewModel)
            assertEquals(
                OperationState.Rejected(money("400.00"), ApiError.QuoteUnavailable),
                viewModel.uiState.value.operation,
            )

            viewModel.onRetry()
            runCurrent()

            assertEquals(OperationState.Reviewing(money("400.00")), viewModel.uiState.value.operation)
            assertEquals(1, gateways.purchases.size)
            assertNotNull(viewModel.uiState.value.estimate)

            gateways.purchaseResult = Outcome.Success(purchaseResult())
            confirm(viewModel)
            assertEquals(2, gateways.purchases.size)
        }

    @Test
    fun us48FiveConfirmationsSendASinglePurchase() =
        runTest {
            val viewModel = viewModel()
            review(viewModel, "400,00")

            repeat(5) { viewModel.onConfirm() }
            runCurrent()
            repeat(5) { viewModel.onConfirm() }
            runCurrent()

            assertEquals(1, gateways.purchases.size)
            assertIs<OperationState.Succeeded<*>>(viewModel.uiState.value.operation)
        }

    @Test
    fun anUncertainResultIsNeverResent() =
        runTest {
            gateways.purchaseResult = Outcome.Failure(OperationError.Uncertain)
            val viewModel = viewModel()
            review(viewModel, "400,00")

            confirm(viewModel)
            viewModel.onRetry()
            viewModel.onConfirm()
            runCurrent()

            assertEquals(OperationState.Uncertain, viewModel.uiState.value.operation)
            assertNull(viewModel.uiState.value.estimate)
            assertEquals(1, gateways.purchases.size)
            assertTrue(announcer.announcements.isEmpty())
        }

    @Test
    fun aNewPurchaseStartsFromAnEmptyField() =
        runTest {
            val viewModel = viewModel()
            review(viewModel, "400,00")
            confirm(viewModel)

            viewModel.onNewOperation()

            assertEquals(OperationState.Editing(""), viewModel.uiState.value.operation)
        }

    private companion object {
        const val FIRST_INSTANT = "2026-11-09T08:10:00-03:00"
        const val SECOND_INSTANT = "2026-11-09T08:10:10-03:00"
        const val REFRESH_MS = 10_000L
        const val LONG_WAIT_MS = 35_000L
        val TEXTS =
            PurchaseTexts(
                succeeded = "Compra realizada",
                refreshPaused = "Atualização automática pausada",
                refreshResumed = "Atualização automática retomada",
            )
    }
}
