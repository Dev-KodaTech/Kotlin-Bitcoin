package com.kotlinbitcoins.web.state.account

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.FieldError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.format.RegistrationError
import com.kotlinbitcoins.web.core.format.RegistrationField
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.state.session.LoginBanner
import com.kotlinbitcoins.web.state.session.SessionManager
import com.kotlinbitcoins.web.state.session.SessionState
import com.kotlinbitcoins.web.state.support.FakeAuthGateway
import com.kotlinbitcoins.web.state.support.FakeSessionStorage
import com.kotlinbitcoins.web.state.support.FakeTabNetwork
import com.kotlinbitcoins.web.state.support.TestClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class RegisterViewModelTest {
    private val gateway = FakeAuthGateway()
    private val storage = FakeSessionStorage()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Subject(
        val viewModel: RegisterViewModel,
        val session: SessionManager,
    )

    private fun TestScope.subject(): Subject {
        val session = SessionManager(storage, FakeTabNetwork().newTab(), TestClock(testScheduler), backgroundScope)
        return Subject(RegisterViewModel(gateway, session), session)
    }

    private fun RegisterViewModel.fill(
        name: String = "Ana Souza",
        email: String = "ana@example.com",
        password: String = "s3nha-forte",
    ) {
        onNameChange(name)
        onEmailChange(email)
        onPasswordChange(password)
    }

    @Test
    fun invalidSubmitShowsEveryFieldErrorFocusesTheFirstAndSendsNothing() =
        runTest {
            val viewModel = subject().viewModel
            viewModel.fill(name = "  ", email = "ana-sem-arroba", password = "1234567")

            viewModel.onSubmit()
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(
                mapOf(
                    RegistrationField.Name to RegisterFieldError.Local(RegistrationError.NameBlank),
                    RegistrationField.Email to RegisterFieldError.Local(RegistrationError.EmailInvalid),
                    RegistrationField.Password to RegisterFieldError.Local(RegistrationError.PasswordTooShort),
                ),
                state.fieldErrors,
            )
            assertEquals(RegistrationField.Name, state.focusRequest)
            assertTrue(gateway.registerCalls.isEmpty())
            assertEquals(false, state.isSubmitting)
        }

    @Test
    fun focusGoesToTheFirstInvalidFieldInFormOrder() =
        runTest {
            val viewModel = subject().viewModel
            viewModel.fill(password = "123")
            viewModel.onSubmit()
            assertEquals(RegistrationField.Password, viewModel.uiState.value.focusRequest)

            viewModel.onFocusHandled()
            assertNull(viewModel.uiState.value.focusRequest)
        }

    @Test
    fun editingAFieldClearsOnlyItsError() =
        runTest {
            val viewModel = subject().viewModel
            viewModel.fill(name = "", email = "x")
            viewModel.onSubmit()

            viewModel.onNameChange("Ana")

            assertEquals(setOf(RegistrationField.Email), viewModel.uiState.value.fieldErrors.keys)
        }

    @Test
    fun successLogsInWithTheSameCredentialsAndGoesToTheDashboard() =
        runTest {
            val (viewModel, session) = subject().let { it.viewModel to it.session }
            viewModel.fill(name = "  Ana Souza ", email = " ana@example.com ")

            viewModel.onSubmit()
            runCurrent()

            assertEquals(
                listOf(FakeAuthGateway.RegisterCall("Ana Souza", "ana@example.com", "s3nha-forte")),
                gateway.registerCalls,
            )
            assertEquals(listOf(FakeAuthGateway.LoginCall("ana@example.com", "s3nha-forte")), gateway.loginCalls)
            assertIs<SessionState.Active>(session.state.value)
            assertEquals(FakeAuthGateway.TOKEN, session.tokenForRequest())
            assertEquals(AccountNavigation(AppRoute.Dashboard, banner = null), viewModel.uiState.value.navigation)

            viewModel.onNavigationHandled()
            assertNull(viewModel.uiState.value.navigation)
        }

    @Test
    fun emailAlreadyInUseMarksTheEmailFieldKeepsNameAndEmailAndClearsThePassword() =
        runTest {
            gateway.registerResult = Outcome.Failure(ApiError.EmailAlreadyInUse)
            val viewModel = subject().viewModel
            viewModel.fill()

            viewModel.onSubmit()
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(mapOf(RegistrationField.Email to RegisterFieldError.EmailAlreadyInUse), state.fieldErrors)
            assertEquals(RegistrationField.Email, state.focusRequest)
            assertEquals("Ana Souza", state.name)
            assertEquals("ana@example.com", state.email)
            assertEquals("", state.password)
            assertEquals(false, state.isSubmitting)
            assertNull(state.navigation)
            assertTrue(gateway.loginCalls.isEmpty())
        }

    @Test
    fun validationErrorsGoToTheirFieldsAndUnknownFieldsToTheSummary() =
        runTest {
            gateway.registerResult =
                Outcome.Failure(
                    ApiError.Validation(
                        listOf(
                            FieldError("email", "E-mail em formato inválido."),
                            FieldError("password", "A senha deve ter pelo menos 8 caracteres."),
                            FieldError("nickname", "Apelido inválido."),
                        ),
                    ),
                )
            val viewModel = subject().viewModel
            viewModel.fill()

            viewModel.onSubmit()
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(
                mapOf(
                    RegistrationField.Email to RegisterFieldError.Server("E-mail em formato inválido."),
                    RegistrationField.Password to
                        RegisterFieldError.Server(
                            "A senha deve ter pelo menos 8 caracteres.",
                        ),
                ),
                state.fieldErrors,
            )
            assertEquals(listOf("Apelido inválido."), state.otherFieldMessages)
            assertEquals(RegistrationField.Email, state.focusRequest)
        }

    @Test
    fun accountCreatedButAutomaticLoginFailedGoesToLoginWithItsBanner() =
        runTest {
            gateway.loginResult = Outcome.Failure(ApiError.NoConnection)
            val (viewModel, session) = subject().let { it.viewModel to it.session }
            viewModel.fill()

            viewModel.onSubmit()
            runCurrent()

            assertEquals(
                AccountNavigation(AppRoute.Login(), LoginBanner.AccountCreated),
                viewModel.uiState.value.navigation,
            )
            assertEquals(SessionState.Resolving, session.state.value)
            assertNull(storage.stored)
        }

    @Test
    fun otherErrorsAreShownForTheWholeFormAndKeepTheInput() =
        runTest {
            gateway.registerResult = Outcome.Failure(ApiError.NoConnection)
            val viewModel = subject().viewModel
            viewModel.fill()

            viewModel.onSubmit()
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(ApiError.NoConnection, state.formError)
            assertEquals("s3nha-forte", state.password)
            assertTrue(state.fieldErrors.isEmpty())
            assertEquals(false, state.isSubmitting)
        }

    @Test
    fun eachFailedSubmitHasANewFeedbackId() =
        runTest {
            gateway.registerResult = Outcome.Failure(ApiError.NoConnection)
            val viewModel = subject().viewModel
            viewModel.fill()
            viewModel.onSubmit()
            runCurrent()
            val first = viewModel.uiState.value.feedbackId
            viewModel.onSubmit()
            runCurrent()
            assertTrue(viewModel.uiState.value.feedbackId > first)
        }

    @Test
    fun repeatedSubmitsSendOnce() =
        runTest {
            gateway.gate = CompletableDeferred()
            val viewModel = subject().viewModel
            viewModel.fill()

            repeat(5) { viewModel.onSubmit() }
            runCurrent()

            assertEquals(1, gateway.registerCalls.size)
            assertEquals(true, viewModel.uiState.value.isSubmitting)
            gateway.gate.complete(Unit)
            runCurrent()
            assertEquals(1, gateway.registerCalls.size)
            assertEquals(1, gateway.loginCalls.size)
        }
}
