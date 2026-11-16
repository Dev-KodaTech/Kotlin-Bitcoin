package com.kotlinbitcoins.web.state.redemption

import com.kotlinbitcoins.web.core.error.AmountInputError
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.RedemptionResult
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.shared.OperationInputError
import com.kotlinbitcoins.web.state.shared.OperationState
import com.kotlinbitcoins.web.state.support.FakeAnnouncer
import com.kotlinbitcoins.web.state.support.FakeAnnouncer.Announcement.Assertive
import com.kotlinbitcoins.web.state.support.FakeAnnouncer.Announcement.Polite
import com.kotlinbitcoins.web.state.support.FakeDashboardGateways
import com.kotlinbitcoins.web.state.support.FakePageVisibility
import com.kotlinbitcoins.web.state.support.money
import com.kotlinbitcoins.web.state.support.position
import com.kotlinbitcoins.web.state.support.quote
import com.kotlinbitcoins.web.state.support.redemptionResult
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RedemptionViewModelTest {
    private val gateways =
        FakeDashboardGateways().apply {
            positionResult = Outcome.Success(position(total = "800.00", investments = 2, quote = FIRST_QUOTE))
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

    private fun TestScope.viewModel(): RedemptionViewModel =
        RedemptionViewModel(
            investmentGateway = gateways,
            pageVisibility = visibility,
            announcer = announcer,
            texts = { TEXTS },
            scope = backgroundScope,
        ).also { runCurrent() }

    private fun TestScope.review(
        viewModel: RedemptionViewModel,
        input: String,
    ) {
        viewModel.onAmountChange(input)
        viewModel.onReview()
        runCurrent()
    }

    private fun TestScope.confirm(viewModel: RedemptionViewModel) {
        viewModel.onConfirm()
        runCurrent()
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun us61TheAvailableIsTheTotalCurrentValueWithTheBuyQuoteAndItsInstant() =
        runTest {
            val viewModel = viewModel()

            val state = viewModel.uiState.value
            assertEquals(OperationState.Editing(""), state.operation)
            val available = assertIs<Loadable.Loaded<*>>(state.available).value
            assertEquals(position(total = "800.00", investments = 2, quote = FIRST_QUOTE), available)
            assertEquals(1, gateways.positionCalls)
            assertNull(state.estimate)
        }

    @Test
    fun us61TheAvailableRefreshesEveryTenSecondsAndStopsHiddenOrPaused() =
        runTest {
            val viewModel = viewModel()
            gateways.positionResult = Outcome.Success(position(total = "790.00", investments = 2, quote = SECOND_QUOTE))

            advance(REFRESH_MS)

            assertEquals(2, gateways.positionCalls)
            assertEquals(
                Loadable.Loaded(position(total = "790.00", investments = 2, quote = SECOND_QUOTE)),
                viewModel.uiState.value.available,
            )

            visibility.isVisible.value = false
            advance(LONG_WAIT_MS)
            assertEquals(2, gateways.positionCalls)
            visibility.isVisible.value = true
            runCurrent()
            assertEquals(3, gateways.positionCalls)

            viewModel.onToggleAutoRefresh()
            runCurrent()
            advance(LONG_WAIT_MS)
            assertEquals(3, gateways.positionCalls)
            assertTrue(viewModel.uiState.value.isAutoRefreshPaused)
            assertEquals(
                listOf<FakeAnnouncer.Announcement>(Polite("Atualização automática pausada")),
                announcer.announcements,
            )
        }

    @Test
    fun invalidAmountsDoNotAdvanceAndSendNothing() =
        runTest {
            val cases =
                mapOf(
                    "" to AmountInputError.Empty,
                    "0" to AmountInputError.NotPositive,
                    "abc" to AmountInputError.InvalidFormat,
                    "1,234" to AmountInputError.TooManyDecimals,
                )
            val viewModel = viewModel()
            cases.forEach { (input, reason) ->
                review(viewModel, input)
                confirm(viewModel)

                assertEquals(
                    OperationState.Editing(input, OperationInputError.Invalid(reason)),
                    viewModel.uiState.value.operation,
                    input,
                )
            }
            assertTrue(gateways.redemptions.isEmpty())
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
    fun us65RedeemAllFillsTheFieldWithTheAvailableWithoutConfirming() =
        runTest {
            val viewModel = viewModel()

            viewModel.onRedeemAll()
            runCurrent()

            assertEquals(OperationState.Editing("800,00"), viewModel.uiState.value.operation)
            assertTrue(gateways.redemptions.isEmpty())
        }

    @Test
    fun us62TheReviewShowsTheEstimateByTheBuyQuote() =
        runTest {
            val viewModel = viewModel()

            review(viewModel, "500,00")

            val state = viewModel.uiState.value
            assertEquals(OperationState.Reviewing(money("500.00")), state.operation)
            val estimate = assertNotNull(state.estimate)
            assertEquals("0,00125000 BTC", estimate.btc.format())
            assertEquals(FIRST_QUOTE, estimate.quote)
            assertFalse(estimate.fromRedeemAll)
            assertTrue(gateways.redemptions.isEmpty())
        }

    @Test
    fun us62ANewQuoteInTheReviewRecalculatesTheEstimate() =
        runTest {
            val viewModel = viewModel()
            review(viewModel, "500,00")

            gateways.positionResult =
                Outcome.Success(position(total = "1000.00", investments = 2, quote = SECOND_QUOTE))
            advance(REFRESH_MS)

            val estimate = assertNotNull(viewModel.uiState.value.estimate)
            assertEquals("0,00100000 BTC", estimate.btc.format())
            assertEquals(SECOND_QUOTE.quotedAt, estimate.quote.quotedAt)
        }

    @Test
    fun us65TheReviewOfRedeemAllWarnsThatTheAmountMayExceedTheAvailable() =
        runTest {
            val viewModel = viewModel()
            viewModel.onRedeemAll()
            viewModel.onReview()
            runCurrent()

            assertTrue(assertNotNull(viewModel.uiState.value.estimate).fromRedeemAll)

            // Typing a new amount is no longer "Resgatar tudo".
            viewModel.onEdit()
            review(viewModel, "799,99")
            assertFalse(assertNotNull(viewModel.uiState.value.estimate).fromRedeemAll)
        }

    @Test
    fun us63TheResultHasTheLiquidationsAndTheReinvestmentAndIsAnnounced() =
        runTest {
            gateways.redemptionResult = Outcome.Success(redemptionResult())
            val viewModel = viewModel()
            review(viewModel, "500,00")

            confirm(viewModel)

            val state = viewModel.uiState.value
            val result = assertIs<OperationState.Succeeded<RedemptionResult>>(state.operation).result
            assertEquals(redemptionResult(), result)
            assertEquals(listOf(false, true), result.liquidations.map { it.isPartial })
            assertNotNull(result.reinvestment)
            assertNull(state.estimate)
            assertEquals(listOf("500.00"), gateways.redemptions.map { it.toApiString() })
            assertEquals(listOf<FakeAnnouncer.Announcement>(Assertive("Resgate realizado")), announcer.announcements)
        }

    @Test
    fun us64AResultWithoutPartialLiquidationHasNoReinvestment() =
        runTest {
            gateways.redemptionResult = Outcome.Success(redemptionResult(withReinvestment = false))
            val viewModel = viewModel()
            review(viewModel, "500,00")

            confirm(viewModel)

            val result = assertIs<OperationState.Succeeded<RedemptionResult>>(viewModel.uiState.value.operation).result
            assertNull(result.reinvestment)
        }

    @Test
    fun us66AboveTheAvailableReloadsItAndRedeemAllWorksAgain() =
        runTest {
            gateways.redemptionResult = Outcome.Failure(OperationError.Rejected(ApiError.RedemptionExceedsAvailable))
            val viewModel = viewModel()
            review(viewModel, "800,01")
            gateways.positionResult = Outcome.Success(position(total = "780.00", investments = 2, quote = SECOND_QUOTE))

            confirm(viewModel)

            val state = viewModel.uiState.value
            assertEquals(OperationState.Rejected(money("800.01"), ApiError.RedemptionExceedsAvailable), state.operation)
            assertEquals(
                Loadable.Loaded(position(total = "780.00", investments = 2, quote = SECOND_QUOTE)),
                state.available,
            )
            assertEquals(2, gateways.positionCalls)
            assertTrue(state.canRedeemAll)

            viewModel.onRedeemAll()
            runCurrent()
            assertEquals(OperationState.Editing("780,00"), viewModel.uiState.value.operation)
            assertEquals(1, gateways.redemptions.size)
        }

    @Test
    fun us67AnEmptyAccountShowsZeroAndStillAllowsSending() =
        runTest {
            gateways.positionResult = Outcome.Success(position(total = "0.00", investments = 0, quote = FIRST_QUOTE))
            gateways.redemptionResult = Outcome.Failure(OperationError.Rejected(ApiError.RedemptionExceedsAvailable))
            val viewModel = viewModel()

            val available = assertIs<Loadable.Loaded<*>>(viewModel.uiState.value.available).value
            assertEquals(position(total = "0.00", investments = 0, quote = FIRST_QUOTE), available)
            assertTrue(viewModel.uiState.value.isEmpty)

            review(viewModel, "10,00")
            confirm(viewModel)

            assertEquals(listOf("10.00"), gateways.redemptions.map { it.toApiString() })
            assertEquals(
                OperationState.Rejected(money("10.00"), ApiError.RedemptionExceedsAvailable),
                viewModel.uiState.value.operation,
            )
        }

    @Test
    fun us68InvalidAmountRefusedByTheApiGoesBackToTheField() =
        runTest {
            gateways.redemptionResult = Outcome.Failure(OperationError.Rejected(ApiError.InvalidAmount))
            val viewModel = viewModel()
            review(viewModel, "0,01")

            confirm(viewModel)

            assertEquals(
                OperationState.Editing("0,01", OperationInputError.RefusedByApi),
                viewModel.uiState.value.operation,
            )
        }

    @Test
    fun us68QuoteUnavailableOnConfirmationOffersANewReviewWithoutSending() =
        runTest {
            gateways.redemptionResult = Outcome.Failure(OperationError.Rejected(ApiError.QuoteUnavailable))
            val viewModel = viewModel()
            review(viewModel, "500,00")
            confirm(viewModel)
            assertEquals(
                OperationState.Rejected(money("500.00"), ApiError.QuoteUnavailable),
                viewModel.uiState.value.operation,
            )

            viewModel.onRetry()
            runCurrent()

            assertEquals(OperationState.Reviewing(money("500.00")), viewModel.uiState.value.operation)
            assertEquals(1, gateways.redemptions.size)

            gateways.redemptionResult = Outcome.Success(redemptionResult())
            confirm(viewModel)
            assertEquals(2, gateways.redemptions.size)
        }

    @Test
    fun anUnavailableQuoteHasNoEstimateNorRedeemAllAndKeepsTheConfirmation() =
        runTest {
            gateways.positionResult = Outcome.Failure(ApiError.QuoteUnavailable)
            val viewModel = viewModel()

            assertEquals(Loadable.Failed(ApiError.QuoteUnavailable), viewModel.uiState.value.available)
            assertFalse(viewModel.uiState.value.canRedeemAll)
            viewModel.onRedeemAll()
            assertEquals(OperationState.Editing(""), viewModel.uiState.value.operation)

            review(viewModel, "500,00")
            assertNull(viewModel.uiState.value.estimate)
            confirm(viewModel)
            assertEquals(1, gateways.redemptions.size)
        }

    @Test
    fun us69FiveConfirmationsSendASingleRedemption() =
        runTest {
            val viewModel = viewModel()
            review(viewModel, "500,00")

            repeat(5) { viewModel.onConfirm() }
            runCurrent()
            repeat(5) { viewModel.onConfirm() }
            runCurrent()

            assertEquals(1, gateways.redemptions.size)
            assertIs<OperationState.Succeeded<*>>(viewModel.uiState.value.operation)
        }

    @Test
    fun anUncertainResultIsNeverResent() =
        runTest {
            gateways.redemptionResult = Outcome.Failure(OperationError.Uncertain)
            val viewModel = viewModel()
            review(viewModel, "500,00")

            confirm(viewModel)
            viewModel.onRetry()
            viewModel.onConfirm()
            runCurrent()

            assertEquals(OperationState.Uncertain, viewModel.uiState.value.operation)
            assertNull(viewModel.uiState.value.estimate)
            assertEquals(1, gateways.redemptions.size)
            assertTrue(announcer.announcements.isEmpty())
        }

    @Test
    fun aNewRedemptionStartsFromAnEmptyField() =
        runTest {
            val viewModel = viewModel()
            viewModel.onRedeemAll()
            viewModel.onReview()
            confirm(viewModel)

            viewModel.onNewOperation()
            review(viewModel, "100,00")

            assertEquals(OperationState.Reviewing(money("100.00")), viewModel.uiState.value.operation)
            assertFalse(assertNotNull(viewModel.uiState.value.estimate).fromRedeemAll)
        }

    private companion object {
        val FIRST_QUOTE = quote(buy = "400000.00", sell = "401000.00", at = "2026-11-09T09:00:00-03:00")
        val SECOND_QUOTE = quote(buy = "500000.00", sell = "501000.00", at = "2026-11-09T09:00:10-03:00")
        const val REFRESH_MS = 10_000L
        const val LONG_WAIT_MS = 35_000L
        val TEXTS =
            RedemptionTexts(
                succeeded = "Resgate realizado",
                refreshPaused = "Atualização automática pausada",
                refreshResumed = "Atualização automática retomada",
            )
    }
}
