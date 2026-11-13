package com.kotlinbitcoins.web.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.app_name
import com.kotlinbitcoins.web.ui.resources.nav_close_menu
import com.kotlinbitcoins.web.ui.resources.nav_current_page
import com.kotlinbitcoins.web.ui.resources.nav_logout
import com.kotlinbitcoins.web.ui.resources.nav_main
import com.kotlinbitcoins.web.ui.resources.nav_menu
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val CompactMaxWidth = 600.dp
private val MediumMaxWidth = 840.dp
private val ContentMaxWidth = 720.dp
private val ContentPadding = 16.dp

/**
 * Adaptive navigation (W-14, FR-009, FR-044): < 600 dp top bar + "Menu" + modal drawer; 600–839 dp navigation
 * rail; ≥ 840 dp permanent drawer. The 7 destinations and "Sair" in all of them, the current one selected and named
 * "…, página atual" (L6 of W-00). Content in one column of at most 720 dp; no horizontal scroll at page level.
 * Texts of Material 3 components come from strings.xml (FR-048).
 */
@Composable
fun AdaptiveScaffold(
    current: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        when {
            maxWidth < CompactMaxWidth -> CompactLayout(current, onNavigate, onLogout, content)
            maxWidth < MediumMaxWidth -> MediumLayout(current, onNavigate, onLogout, content)
            else -> ExpandedLayout(current, onNavigate, onLogout, content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactLayout(
    current: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val closeMenu = stringResource(Res.string.nav_close_menu)
    val close: () -> Unit = { scope.launch { drawerState.close() } }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.semantics { paneTitle = closeMenu }) {
                DrawerItems(
                    current = current,
                    onNavigate = {
                        close()
                        onNavigate(it)
                    },
                    onLogout = {
                        close()
                        onLogout()
                    },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.app_name)) },
                    navigationIcon = {
                        TextButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
                        ) {
                            Text(stringResource(Res.string.nav_menu))
                        }
                    },
                )
            },
        ) { padding ->
            PageContent(Modifier.padding(padding), content)
        }
    }
}

@Composable
private fun MediumLayout(
    current: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    val navigationName = stringResource(Res.string.nav_main)
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(modifier = Modifier.semantics { contentDescription = navigationName }) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                navigationItems.forEach { item ->
                    val label = stringResource(item.label)
                    val isCurrent = item.route.isSameDestination(current)
                    NavigationRailItem(
                        selected = isCurrent,
                        onClick = { onNavigate(item.route) },
                        icon = {},
                        label = { Text(label) },
                        alwaysShowLabel = true,
                        modifier = Modifier.focusOutline().currentPage(label, isCurrent),
                    )
                }
                NavigationRailItem(
                    selected = false,
                    onClick = onLogout,
                    icon = {},
                    label = { Text(stringResource(Res.string.nav_logout)) },
                    alwaysShowLabel = true,
                    modifier = Modifier.focusOutline(),
                )
            }
        }
        PageContent(Modifier.weight(1f), content)
    }
}

@Composable
private fun ExpandedLayout(
    current: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(modifier = Modifier.widthIn(max = 280.dp)) {
                DrawerItems(current, onNavigate, onLogout)
            }
        },
    ) {
        PageContent(Modifier.fillMaxSize(), content)
    }
}

@Composable
private fun DrawerItems(
    current: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(12.dp),
        )
        navigationItems.forEach { item ->
            val label = stringResource(item.label)
            val isCurrent = item.route.isSameDestination(current)
            NavigationDrawerItem(
                label = { Text(label) },
                selected = isCurrent,
                onClick = { onNavigate(item.route) },
                modifier = Modifier.focusOutline().currentPage(label, isCurrent),
            )
        }
        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.nav_logout)) },
            selected = false,
            onClick = onLogout,
            modifier = Modifier.focusOutline(),
        )
    }
}

/** L6 of W-00: the mirror drops `selected`, so the current page is also part of the name. */
@Composable
private fun Modifier.currentPage(
    label: String,
    isCurrent: Boolean,
): Modifier {
    val currentName = stringResource(Res.string.nav_current_page, label)
    return semantics {
        selected = isCurrent
        if (isCurrent) contentDescription = currentName
    }
}

@Composable
private fun PageContent(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // No scroll here: each screen scrolls itself (a LazyColumn cannot live inside a verticalScroll).
        Box(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(ContentPadding)) {
            content()
        }
    }
}
