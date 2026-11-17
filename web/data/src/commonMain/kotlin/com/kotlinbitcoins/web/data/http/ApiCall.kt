package com.kotlinbitcoins.web.data.http

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.OperationError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.state.port.SessionTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * The error boundary of the client (W-05, Principle III): the only place of :data where exceptions of Ktor, fetch
 * and decoding are caught. Everything leaves as [Outcome]:
 * - [read] (reads, login, registration): transport failure → [ApiError.NoConnection];
 * - [financial] (deposit, purchase, redemption): a Problem is a definitive [OperationError.Rejected]; a transport
 *   failure, a timeout, 502/504 without Problem or a success body outside the contract → [OperationError.Uncertain].
 * A 401 UNAUTHORIZED also ends the session through [SessionTokenProvider.onUnauthorized].
 */
class ApiCall(
    private val client: HttpClient,
    private val tokenProvider: SessionTokenProvider,
    private val json: Json = HttpClientFactory.json,
) {
    suspend fun <D, T> read(
        serializer: DeserializationStrategy<D>,
        map: (D) -> Outcome<ApiError, T>,
        request: HttpRequestBuilder.() -> Unit,
    ): Outcome<ApiError, T> =
        when (val exchange = exchange(serializer, request)) {
            is Exchange.Success -> map(exchange.body)
            Exchange.OutsideContract -> Outcome.Failure(ApiError.Unexpected)
            is Exchange.Failed -> Outcome.Failure(exchange.problem?.let(::toError) ?: ApiError.Unexpected)
            Exchange.TransportFailure -> Outcome.Failure(ApiError.NoConnection)
            Exchange.NotSent -> Outcome.Failure(ApiError.Unauthorized)
        }

    suspend fun <D, T> financial(
        serializer: DeserializationStrategy<D>,
        map: (D) -> Outcome<ApiError, T>,
        request: HttpRequestBuilder.() -> Unit,
    ): Outcome<OperationError, T> =
        when (val exchange = exchange(serializer, request)) {
            is Exchange.Success -> {
                when (val mapped = map(exchange.body)) {
                    is Outcome.Success -> mapped

                    // The API answered 2xx: the operation happened, but its result cannot be shown.
                    is Outcome.Failure -> Outcome.Failure(OperationError.Uncertain)
                }
            }

            Exchange.OutsideContract, Exchange.TransportFailure -> {
                Outcome.Failure(OperationError.Uncertain)
            }

            is Exchange.Failed -> {
                Outcome.Failure(failedOperation(exchange))
            }

            Exchange.NotSent -> {
                Outcome.Failure(OperationError.Rejected(ApiError.Unauthorized))
            }
        }

    private fun failedOperation(failed: Exchange.Failed): OperationError {
        val problem = failed.problem
        return when {
            problem != null -> OperationError.Rejected(toError(problem))
            failed.status in GATEWAY_FAILURES -> OperationError.Uncertain
            else -> OperationError.Rejected(ApiError.Unexpected)
        }
    }

    private fun toError(problem: ProblemDto): ApiError =
        ProblemMapper.map(problem).also { if (it == ApiError.Unauthorized) tokenProvider.onUnauthorized() }

    private suspend fun <D> exchange(
        serializer: DeserializationStrategy<D>,
        request: HttpRequestBuilder.() -> Unit,
    ): Exchange<D> {
        val received =
            attempt {
                val response = client.request(request)
                response.status.value to response.bodyAsText()
            }
        return when (received) {
            is Attempt.Done -> {
                val (status, text) = received.value
                if (status in SUCCESS) {
                    when (val decoded = attempt { json.decodeFromString(serializer, text) }) {
                        is Attempt.Done -> Exchange.Success(decoded.value)
                        else -> Exchange.OutsideContract
                    }
                } else {
                    val problem = attempt { json.decodeFromString(ProblemDto.serializer(), text) }
                    Exchange.Failed(status, (problem as? Attempt.Done)?.value?.takeIf { it.code != null })
                }
            }

            Attempt.NotSent -> {
                Exchange.NotSent
            }

            Attempt.Failed -> {
                Exchange.TransportFailure
            }
        }
    }

    /** The single exception handler of the module. Cancellation is never swallowed. */
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> attempt(block: () -> T): Attempt<T> =
        try {
            Attempt.Done(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SessionUnavailableException) {
            Attempt.NotSent
        } catch (_: Throwable) {
            Attempt.Failed
        }

    private sealed interface Attempt<out T> {
        data class Done<out T>(
            val value: T,
        ) : Attempt<T>

        data object NotSent : Attempt<Nothing>

        data object Failed : Attempt<Nothing>
    }

    private sealed interface Exchange<out D> {
        data class Success<out D>(
            val body: D,
        ) : Exchange<D>

        /** 2xx whose body does not decode into the DTO. */
        data object OutsideContract : Exchange<Nothing>

        /** Non-2xx; [problem] is null when the body is not a Problem of the contract. */
        data class Failed(
            val status: Int,
            val problem: ProblemDto?,
        ) : Exchange<Nothing>

        data object TransportFailure : Exchange<Nothing>

        /** Authenticated request without a valid session: never left the browser. */
        data object NotSent : Exchange<Nothing>
    }

    private companion object {
        val SUCCESS = 200..299
        val GATEWAY_FAILURES = setOf(HttpStatusCode.BadGateway.value, HttpStatusCode.GatewayTimeout.value)
    }
}
