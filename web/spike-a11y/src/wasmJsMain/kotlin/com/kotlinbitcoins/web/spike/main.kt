package com.kotlinbitcoins.web.spike

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

@Serializable
data object FormRoute

@Serializable
data class TableRoute(
    val filter: String? = null,
)

private const val APP_NAME = "Investimento em Bitcoins"

@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

external fun encodeURIComponent(value: String): String

external fun decodeURIComponent(value: String): String

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Mandatory reference: keeps the named time zone data in the bundle (W-07).
    JsJodaTimeZoneModule
    // "?mitigacoes=0" shows the raw Compose mirror, for comparison in the spike report.
    if (!window.location.search.contains("mitigacoes=0")) installComposeA11yShim()
    ComposeViewport(viewportContainerId = "compose-root") {
        SpikeApp(initialRoute = parseFragment(window.location.hash))
    }
}

/** `#/login`, `#/form`, `#/tabela?filtro=x` → route; anything else → login (W-04: read the fragment at start). */
fun parseFragment(hash: String): Any {
    val fragment = hash.removePrefix("#")
    val path = fragment.substringBefore('?')
    val query = fragment.substringAfter('?', "")
    return when (path) {
        "/form" -> {
            FormRoute
        }

        "/tabela" -> {
            val filter =
                query
                    .split('&')
                    .map { it.substringBefore('=') to it.substringAfter('=', "") }
                    .firstOrNull { it.first == "filtro" }
                    ?.second
                    ?.let(::decodeURIComponent)
            TableRoute(filter)
        }

        else -> {
            LoginRoute
        }
    }
}

private fun fragmentOf(entry: NavBackStackEntry): String {
    val destination = entry.destination
    return when {
        destination.hasRoute<FormRoute>() -> {
            "#/form"
        }

        destination.hasRoute<TableRoute>() -> {
            val filter = entry.toRoute<TableRoute>().filter
            if (filter.isNullOrEmpty()) "#/tabela" else "#/tabela?filtro=" + encodeURIComponent(filter)
        }

        else -> {
            "#/login"
        }
    }
}

private fun titleOf(entry: NavBackStackEntry?): String {
    val destination = entry?.destination ?: return APP_NAME
    val screen =
        when {
            destination.hasRoute<FormRoute>() -> "Criar conta"
            destination.hasRoute<TableRoute>() -> "Extrato"
            else -> "Entrar"
        }
    return "$screen · $APP_NAME"
}

@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
fun SpikeApp(initialRoute: Any) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()

    LaunchedEffect(navController) {
        navController.bindToBrowserNavigation(::fragmentOf)
    }
    LaunchedEffect(entry) {
        document.title = titleOf(entry)
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                SpikeNavigation(navController, entry)
                HorizontalDivider()
                NavHost(navController = navController, startDestination = initialRoute) {
                    composable<LoginRoute> { LoginSpike() }
                    composable<FormRoute> { RegisterFormSpike() }
                    composable<TableRoute> { backStackEntry ->
                        StatementTableSpike(
                            filter = backStackEntry.toRoute<TableRoute>().filter,
                            onApplyFilter = { navController.navigate(TableRoute(it.ifBlank { null })) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpikeNavigation(
    navController: NavHostController,
    entry: NavBackStackEntry?,
) {
    val destination = entry?.destination
    val links =
        listOf(
            Triple("Entrar", LoginRoute, destination?.hasRoute<LoginRoute>() == true),
            Triple("Criar conta", FormRoute, destination?.hasRoute<FormRoute>() == true),
            Triple("Extrato", TableRoute(), destination?.hasRoute<TableRoute>() == true),
        )
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        links.forEach { (label, route, isCurrent) ->
            TextButton(
                onClick = { navController.navigate(route) { launchSingleTop = true } },
                modifier = Modifier.focusOutline().semantics { selected = isCurrent },
            ) {
                Text(label)
            }
        }
    }
}
