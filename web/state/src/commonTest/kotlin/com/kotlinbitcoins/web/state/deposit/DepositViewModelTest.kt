package com.kotlinbitcoins.web.state.deposit

import com.kotlinbitcoins.web.core.error.AmountInputError
import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.DepositResult
import com.kotlinbitcoins.web.core.model.EntryType
import com.kotlinbitcoins.web.core.model.StatementEntry
import com.kotlinbitcoins.web.state.shared.Loadable
import com.kotlinbitcoins.web.state.shared.OperationInputError
import com.kotlinbitcoins.web.state.shared.OperationState
import com.kotlinbitcoins.web.state.support.FakeAnnouncer
import com.kotlinbitcoins.web.state.support.FakeAnnouncer.Announcement.Assertive
import com.kotlinbitcoins.web.state.support.FakeDashboardGateways
import com.kotlinbitcoins.web.state.support.FakeStatementGateway
import com.kotlinbitcoins.web.state.support.depositResult
import com.kotlinbitcoins.web.state.support.money
import com.kotlinbitcoins.web.state.support.statement
import com.kotlinbitcoins.web.state.support.statementEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DepositViewModelTest {
    private val gateway = FakeDashboardGateways()
    private val statements = FakeStatementGateway()
    private val announcer = FakeAnnouncer()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): DepositViewModel =
        DepositViewModel(
            accountGateway = gateway,
            statementGateway = statements,
            announcer = announcer,
            texts = { TEXTS },
            scope = backgroundScope,
        ).also { runCurrent() }

    private fun TestScope.deposit(
        viewModel: DepositViewModel,
        input: String,
    ) {
        viewModel.onAmountChange(input)
        viewModel.onConfirm()
        runCurrent()
    }

    @Test
    fun startsEditingAnEmptyAmount() =
        runTest {
            assertEquals(OperationState.Editing(""), viewModel().uiState.value.operation)
        }

    @Test
    fun us31AValidDepositShowsTheAmountTheNewBalanceAndTheInstantOfTheApi() =
        runTest {
            gateway.depositResult = Outcome.Success(depositResult(amount = "100.00", balance = "150.00"))
            val viewModel = viewModel()

            deposit(viewModel, "100,00")

            val result = assertIs<OperationState.Succeeded<DepositResult>>(viewModel.uiState.value.operation).result
            assertEquals(depositResult(amount = "100.00", balance = "150.00"), result)
            assertEquals("R$ 100,00", result.amount.format())
            assertEquals("R$ 150,00", result.balance.format())
            assertEquals(listOf("100.00"), gateway.deposits.map { it.toApiString() })
            assertEquals(listOf<FakeAnnouncer.Announcement>(Assertive("Depósito realizado")), announcer.announcements)
        }

    @Test
    fun us32InvalidAmountsShowTheErrorOnTheFieldWithoutCallingTheApi() =
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
                deposit(viewModel, input)

                assertEquals(
                    OperationState.Editing(input, OperationInputError.Invalid(reason)),
                    viewModel.uiState.value.operation,
                    input,
                )
            }
            assertTrue(gateway.deposits.isEmpty())
            assertTrue(announcer.announcements.isEmpty())
        }

    @Test
    fun everyConfirmationChangesTheAttemptSoTheFieldTakesTheFocusAgain() =
        runTest {
            val viewModel = viewModel()
            deposit(viewModel, "")
            val first = viewModel.uiState.value.attempt

            deposit(viewModel, "")

            assertEquals(first + 1, viewModel.uiState.value.attempt)
        }

    @Test
    fun us33TheBrazilianFormatIsSentAsTheContractString() =
        runTest {
            deposit(viewModel(), "1.234,56")
            assertEquals(listOf("1234.56"), gateway.deposits.map { it.toApiString() })
        }

    @Test
    fun us34FiveConfirmationsSendASingleDeposit() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAmountChange("100,00")

            repeat(5) { viewModel.onConfirm() }
            runCurrent()
            repeat(5) { viewModel.onConfirm() }
            runCurrent()

            assertEquals(1, gateway.deposits.size)
            assertIs<OperationState.Succeeded<*>>(viewModel.uiState.value.operation)
        }

    @Test
    fun anAmountRefusedByTheApiGoesBackToTheFieldWithTheMessage() =
        runTest {
            gateway.depositResult = Outcome.Failure(OperationError.Rejected(ApiError.InvalidAmount))
            val viewModel = viewModel()

            deposit(viewModel, "0,01")

            assertEquals(
                OperationState.Editing("0,01", OperationInputError.RefusedByApi),
                viewModel.uiState.value.operation,
            )
            assertTrue(announcer.announcements.isEmpty())
        }

    @Test
    fun anUncertainResultIsNeverResent() =
        runTest {
            gateway.depositResult = Outcome.Failure(OperationError.Uncertain)
            val viewModel = viewModel()

            deposit(viewModel, "100,00")
            viewModel.onRetry()
            viewModel.onConfirm()
            runCurrent()

            assertEquals(OperationState.Uncertain, viewModel.uiState.value.operation)
            assertEquals(1, gateway.deposits.size)
            assertTrue(announcer.announcements.isEmpty())
        }

    @Test
    fun aServiceUnavailableDepositCanBeTriedAgainWithANewConfirmation() =
        runTest {
            gateway.depositResult = Outcome.Failure(OperationError.Rejected(ApiError.ServiceUnavailable))
            val viewModel = viewModel()
            deposit(viewModel, "100,00")
            assertEquals(
                OperationState.Rejected(gateway.deposits.single(), ApiError.ServiceUnavailable),
                viewModel.uiState.value.operation,
            )

            viewModel.onRetry()
            assertEquals(OperationState.Editing("100,00"), viewModel.uiState.value.operation)
            assertEquals(1, gateway.deposits.size)

            gateway.depositResult = Outcome.Success(depositResult())
            viewModel.onConfirm()
            runCurrent()
            assertEquals(2, gateway.deposits.size)
        }

    @Test
    fun aNewDepositStartsFromAnEmptyField() =
        runTest {
            val viewModel = viewModel()
            deposit(viewModel, "100,00")

            viewModel.onNewOperation()

            assertEquals(OperationState.Editing(""), viewModel.uiState.value.operation)
        }

    @Test
    fun theBalanceAndTheNewestDepositsLoadWhenThePageOpens() =
        runTest {
            gateway.balanceResult = Outcome.Success(money("50.00"))
            statements.result =
                Outcome.Success(
                    statement(
                        entries =
                            listOf(
                                statementEntry("d4", EntryType.Deposit, "40.00"),
                                statementEntry("p", EntryType.Purchase, "400.00"),
                                statementEntry("d3", EntryType.Deposit, "30.00"),
                                statementEntry("d2", EntryType.Deposit, "20.00"),
                                statementEntry("d1", EntryType.Deposit, "10.00"),
                            ),
                    ),
                )

            val state = viewModel().uiState.value

            assertEquals(Loadable.Loaded(money("50.00")), state.balance)
            assertEquals(listOf("d4", "d3", "d2"), state.recentDepositIds())
        }

    @Test
    fun aStatementWithoutDepositsIsTheEmptyStateOfTheNewestDeposits() =
        runTest {
            statements.result =
                Outcome.Success(statement(entries = listOf(statementEntry("p", EntryType.Purchase, "400.00"))))

            assertEquals(Loadable.Empty, viewModel().uiState.value.recentDeposits)
        }

    @Test
    fun theBlocksFailAloneAndCanBeReadAgain() =
        runTest {
            gateway.balanceResult = Outcome.Failure(ApiError.ServiceUnavailable)
            statements.result = Outcome.Failure(ApiError.NoConnection)
            val viewModel = viewModel()
            assertEquals(Loadable.Failed(ApiError.ServiceUnavailable), viewModel.uiState.value.balance)
            assertEquals(Loadable.Failed(ApiError.NoConnection), viewModel.uiState.value.recentDeposits)
            assertEquals(OperationState.Editing(""), viewModel.uiState.value.operation)

            gateway.balanceResult = Outcome.Success(money("50.00"))
            viewModel.onRetryBalance()
            runCurrent()
            assertEquals(Loadable.Loaded(money("50.00")), viewModel.uiState.value.balance)
            assertEquals(Loadable.Failed(ApiError.NoConnection), viewModel.uiState.value.recentDeposits)

            statements.result = Outcome.Success(statement())
            viewModel.onRetryRecentDeposits()
            runCurrent()
            assertEquals(listOf("d"), viewModel.uiState.value.recentDepositIds())
        }

    @Test
    fun aValidAmountBeingTypedHasThePreviewOfTheBalance() =
        runTest {
            gateway.balanceResult = Outcome.Success(money("50.00"))
            val viewModel = viewModel()
            assertNull(viewModel.uiState.value.preview)

            viewModel.onAmountChange("1.234,56")

            assertEquals(DepositPreview(money("1234.56"), money("1284.56")), viewModel.uiState.value.preview)
        }

    @Test
    fun anAmountThatIsNotValidHasNoPreview() =
        runTest {
            val viewModel = viewModel()
            listOf("", "0", "abc", "1,234").forEach { input ->
                viewModel.onAmountChange(input)
                assertNull(viewModel.uiState.value.preview, input)
            }
        }

    @Test
    fun withoutTheBalanceThePreviewHasOnlyTheAmount() =
        runTest {
            gateway.balanceResult = Outcome.Failure(ApiError.ServiceUnavailable)
            val viewModel = viewModel()

            viewModel.onAmountChange("100,00")
            assertEquals(DepositPreview(money("100.00"), balanceAfter = null), viewModel.uiState.value.preview)

            gateway.balanceResult = Outcome.Success(money("50.00"))
            viewModel.onRetryBalance()
            runCurrent()
            assertEquals(DepositPreview(money("100.00"), money("150.00")), viewModel.uiState.value.preview)
        }

    @Test
    fun theQuickAmountsFillTheFieldInTheBrazilianFormat() =
        runTest {
            val viewModel = viewModel()
            val quick = viewModel.uiState.value.quickAmounts
            assertEquals(listOf("100.00", "250.00", "500.00", "1000.00"), quick.map { it.toApiString() })

            viewModel.onAmountChange(quick.last().toInputString())

            assertEquals(OperationState.Editing("1.000,00"), viewModel.uiState.value.operation)
            assertEquals(
                quick.last(),
                viewModel.uiState.value.preview
                    ?.amount,
            )
        }

    @Test
    fun afterADepositTheBalanceIsTheOneOfTheApiAndTheDepositsAreReadAgain() =
        runTest {
            gateway.balanceResult = Outcome.Success(money("50.00"))
            gateway.depositResult = Outcome.Success(depositResult(amount = "100.00", balance = "150.00"))
            val viewModel = viewModel()
            statements.result =
                Outcome.Success(statement(entries = listOf(statementEntry("new", EntryType.Deposit, "100.00"))))

            deposit(viewModel, "100,00")

            val state = viewModel.uiState.value
            assertEquals(Loadable.Loaded(money("150.00")), state.balance)
            assertNull(state.preview)
            assertEquals(listOf("new"), state.recentDepositIds())
            assertEquals(1, gateway.balanceCalls)
        }

    @Test
    fun anUncertainResultReadsTheBalanceAndTheDepositsAgain() =
        runTest {
            gateway.balanceResult = Outcome.Success(money("50.00"))
            gateway.depositResult = Outcome.Failure(OperationError.Uncertain)
            val viewModel = viewModel()
            gateway.balanceResult = Outcome.Success(money("150.00"))

            deposit(viewModel, "100,00")

            assertEquals(Loadable.Loaded(money("150.00")), viewModel.uiState.value.balance)
            assertEquals(2, statements.filters.size)
        }

    private fun DepositUiState.recentDepositIds(): List<String> =
        assertIs<Loadable.Loaded<List<StatementEntry>>>(recentDeposits).value.map { it.id }

    private companion object {
        val TEXTS = DepositTexts(succeeded = "Depósito realizado")
    }
}
