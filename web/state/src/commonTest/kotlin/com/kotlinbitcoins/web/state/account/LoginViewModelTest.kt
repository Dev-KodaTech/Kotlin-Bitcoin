package com.kotlinbitcoins.web.state.account

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
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
class LoginViewModelTest {
    private val gateway = FakeAuthGateway()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private lateinit var session: SessionManager

    private fun TestScope.viewModel(
        returnTo: String? = null,
        banner: LoginBanner? = null,
    ): LoginViewModel {
        session =
            SessionManager(FakeSessionStorage(), FakeTabNetwork().newTab(), TestClock(testScheduler), backgroundScope)
        return LoginViewModel(gateway, session, returnTo, banner)
    }

    private fun LoginViewModel.submit(
        email: String = "ana@example.com",
        password: String = "s3nha-forte",
    ) {
        onEmailChange(email)
        onPasswordChange(password)
        onSubmit()
    }

    @Test
    fun startsWithTheBannerOfTheNavigation() =
        runTest {
            assertEquals(
                LoginBanner.SessionExpired,
                viewModel(banner = LoginBanner.SessionExpired).uiState.value.banner,
            )
            assertNull(viewModel().uiState.value.banner)
        }

    @Test
    fun invalidCredentialsShowTheSameGenericBannerForUnknownEmailAndWrongPassword() =
        runTest {
            gateway.loginResult = Outcome.Failure(ApiError.InvalidCredentials)
            val unknownEmail = viewModel()
            unknownEmail.submit(email = "ninguem@example.com")
            runCurrent()
            val wrongPassword = viewModel()
            wrongPassword.submit(password = "senha-errada")
            runCurrent()

            listOf(unknownEmail, wrongPassword).forEach {
                val state = it.uiState.value
                assertEquals(LoginBanner.InvalidCredentials, state.banner)
                assertEquals("", state.password)
                assertEquals(false, state.isSubmitting)
                assertNull(state.navigation)
            }
            assertEquals(unknownEmail.uiState.value.copy(email = ""), wrongPassword.uiState.value.copy(email = ""))
        }

    @Test
    fun successStartsTheSessionAndGoesToTheReturnDestinationWithItsFilter() =
        runTest {
            val viewModel =
                viewModel(returnTo = "/extrato?de=2026-10-01&ate=2026-11-09", banner = LoginBanner.SessionExpired)

            viewModel.submit()
            runCurrent()

            assertEquals(listOf(FakeAuthGateway.LoginCall("ana@example.com", "s3nha-forte")), gateway.loginCalls)
            assertIs<SessionState.Active>(session.state.value)
            assertEquals(FakeAuthGateway.TOKEN, session.tokenForRequest())
            assertEquals(
                AccountNavigation(AppRoute.Statement("2026-10-01", "2026-11-09"), banner = null),
                viewModel.uiState.value.navigation,
            )
        }

    @Test
    fun anExternalOrPublicReturnDestinationGoesToTheDashboard() =
        runTest {
            val refused = listOf("https://evil.example", "//evil.example", "javascript:alert(1)", "/entrar", null)
            refused.forEach { returnTo ->
                val viewModel = viewModel(returnTo = returnTo)
                viewModel.submit()
                runCurrent()
                assertEquals(
                    AccountNavigation(AppRoute.Dashboard, banner = null),
                    viewModel.uiState.value.navigation,
                    "$returnTo",
                )
            }
        }

    @Test
    fun noConnectionShowsTheGenericConnectionMessageAndKeepsTheInput() =
        runTest {
            gateway.loginResult = Outcome.Failure(ApiError.NoConnection)
            val viewModel = viewModel(banner = LoginBanner.SessionExpired)

            viewModel.submit()
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(LoginBanner.Error(ApiError.NoConnection), state.banner)
            assertEquals("ana@example.com", state.email)
            assertEquals("s3nha-forte", state.password)
            assertEquals(SessionState.Resolving, session.state.value)
        }

    @Test
    fun eachFailedSubmitHasANewFeedbackId() =
        runTest {
            gateway.loginResult = Outcome.Failure(ApiError.InvalidCredentials)
            val viewModel = viewModel()
            viewModel.submit()
            runCurrent()
            val first = viewModel.uiState.value.feedbackId
            viewModel.submit()
            runCurrent()
            assertTrue(viewModel.uiState.value.feedbackId > first)
        }

    @Test
    fun repeatedSubmitsSendOnce() =
        runTest {
            gateway.gate = CompletableDeferred()
            val viewModel = viewModel()
            viewModel.onEmailChange("ana@example.com")
            viewModel.onPasswordChange("s3nha-forte")

            repeat(5) { viewModel.onSubmit() }
            runCurrent()

            assertEquals(1, gateway.loginCalls.size)
            assertEquals(true, viewModel.uiState.value.isSubmitting)
            gateway.gate.complete(Unit)
            runCurrent()
            assertEquals(1, gateway.loginCalls.size)
        }
}
