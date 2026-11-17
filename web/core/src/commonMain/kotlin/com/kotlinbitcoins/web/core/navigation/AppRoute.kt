package com.kotlinbitcoins.web.core.navigation

import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.statement.FilterError
import com.kotlinbitcoins.web.core.statement.StatementFilter
import kotlinx.serialization.Serializable

/**
 * Destinations of the app and their URL fragments (contracts/ui-routes.md, data-model §7). Type-safe routes of
 * navigation-compose; the fragment is the only representation in the URL.
 */
@Serializable
sealed interface AppRoute {
    /** Public routes (login and registration) are the only ones reachable without a session. */
    val isProtected: Boolean
        get() = this !is Login && this !is Register

    /** The URL fragment, with the leading `#`, e.g. "#/extrato?de=2026-10-01&ate=2026-11-09". */
    fun toFragment(): String =
        when (this) {
            is Login -> "#/entrar" + query(PARAM_RETURN to returnTo)
            Register -> "#/cadastro"
            Dashboard -> "#/painel"
            Deposit -> "#/depositar"
            Purchase -> "#/comprar"
            Investments -> "#/investimentos"
            Redemption -> "#/resgatar"
            is Statement -> "#/extrato" + query(PARAM_FROM to from, PARAM_TO to to)
            Market -> "#/mercado"
        }

    /** [returnTo] is the fragment of the protected route to open after login, without the `#`. */
    @Serializable
    data class Login(
        val returnTo: String? = null,
    ) : AppRoute

    @Serializable
    data object Register : AppRoute

    @Serializable
    data object Dashboard : AppRoute

    @Serializable
    data object Deposit : AppRoute

    @Serializable
    data object Purchase : AppRoute

    @Serializable
    data object Investments : AppRoute

    @Serializable
    data object Redemption : AppRoute

    /** Keeps the raw `de`/`ate` of the fragment, so that an invalid input is shown back with its error. */
    @Serializable
    data class Statement(
        val from: String? = null,
        val to: String? = null,
    ) : AppRoute {
        fun toFilter(): Outcome<FilterError, StatementFilter> = StatementFilter.fromIso(from, to)

        companion object {
            fun of(filter: StatementFilter): Statement =
                when (filter) {
                    StatementFilter.Default -> Statement()
                    is StatementFilter.Range -> Statement(filter.from.toIso(), filter.to.toIso())
                }
        }
    }

    @Serializable
    data object Market : AppRoute

    companion object {
        private const val PARAM_RETURN = "voltar"
        private const val PARAM_FROM = "de"
        private const val PARAM_TO = "ate"

        /** Resolves a fragment (with or without `#`); empty or unknown fragments resolve to [Dashboard]. */
        fun parse(fragment: String): AppRoute = parseOrNull(fragment) ?: Dashboard

        /** Like [parse], but null for anything that is not a route of the app. */
        fun parseOrNull(fragment: String): AppRoute? {
            val text = fragment.removePrefix("#").ifEmpty { "/" }
            val params = parseQuery(text.substringAfter('?', missingDelimiterValue = ""))
            return when (text.substringBefore('?')) {
                "/" -> Dashboard
                "/entrar" -> Login(returnTo = params[PARAM_RETURN]?.let(UriComponent::decode))
                "/cadastro" -> Register
                "/painel" -> Dashboard
                "/depositar" -> Deposit
                "/comprar" -> Purchase
                "/investimentos" -> Investments
                "/resgatar" -> Redemption
                "/extrato" -> Statement(from = params[PARAM_FROM]?.decodeOrRaw(), to = params[PARAM_TO]?.decodeOrRaw())
                "/mercado" -> Market
                else -> null
            }
        }

        /** Raw (still encoded) values; the first occurrence of a name wins and unknown names are ignored later. */
        private fun parseQuery(query: String): Map<String, String> =
            query
                .split('&')
                .filter { it.isNotEmpty() }
                .map { it.substringBefore('=') to it.substringAfter('=', missingDelimiterValue = "") }
                .distinctBy { it.first }
                .toMap()

        private fun String.decodeOrRaw(): String = UriComponent.decode(this) ?: this

        private fun query(vararg params: Pair<String, String?>): String {
            val present = params.filter { !it.second.isNullOrEmpty() }
            if (present.isEmpty()) return ""
            return present.joinToString("&", prefix = "?") { (name, value) -> "$name=${UriComponent.encode(value!!)}" }
        }
    }
}
