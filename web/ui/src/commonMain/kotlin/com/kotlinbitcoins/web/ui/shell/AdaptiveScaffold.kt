package com.kotlinbitcoins.web.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kotlinbitcoins.web.core.model.Profile
import com.kotlinbitcoins.web.core.navigation.AppRoute
import com.kotlinbitcoins.web.ui.components.LocalDenseLayout
import com.kotlinbitcoins.web.ui.components.MinTouchTarget
import com.kotlinbitcoins.web.ui.components.focusOutline
import com.kotlinbitcoins.web.ui.icons.AppIcons
import com.kotlinbitcoins.web.ui.resources.Res
import com.kotlinbitcoins.web.ui.resources.app_name
import com.kotlinbitcoins.web.ui.resources.nav_close_menu
import com.kotlinbitcoins.web.ui.resources.nav_current_page
import com.kotlinbitcoins.web.ui.resources.nav_logout
import com.kotlinbitcoins.web.ui.resources.nav_main
import com.kotlinbitcoins.web.ui.resources.nav_menu
import com.kotlinbitcoins.web.ui.theme.LocalExtendedColors
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val CompactMaxWidth = 600.dp
private val MediumMaxWidth = 840.dp
private val ContentMaxWidth = 1120.dp
private val ShortMaxHeight = 560.dp
private val RoomyMinHeight = 860.dp
private val SidebarWidth = 268.dp
private val BrandMarkSize = 40.dp
private val AvatarSize = 40.dp

/**
 * Adaptive navigation (W-14, FR-009, FR-044) in the "Azul suave" look: the page is a rounded shell on a tinted
 * ground, with the navigation on its left. < 600 dp top bar + "Menu" + modal drawer; 600–839 dp navigation rail;
 * ≥ 840 dp permanent sidebar. The 7 destinations, the theme switch and "Sair" in all of them, the current one as a
 * filled pill and named "…, página atual" (L6 of W-00). [user] (name and e-mail) is shown above "Sair" once it is
 * known. Content in one column of at most 1120 dp; no horizontal scroll at page level. Texts come from strings.xml
 * (FR-048).
 */
@Composable
fun AdaptiveScaffold(
    current: AppRoute,
    user: Profile?,
    onNavigate: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val isCompact = maxWidth < CompactMaxWidth
        // A short window (a desktop one at 200% zoom, a phone on its side) gets the dense blocks and a thin frame.
        val isShort = maxHeight < ShortMaxHeight
        val frame =
            when {
                isShort -> 0.dp
                maxHeight < RoomyMinHeight -> 12.dp
                else -> 24.dp
            }
        CompositionLocalProvider(LocalDenseLayout provides (isCompact || isShort)) {
            when {
                isCompact -> CompactLayout(current, user, onNavigate, onLogout, content)
                maxWidth < MediumMaxWidth -> MediumLayout(current, isShort, onNavigate, onLogout, content)
                else -> ShellSurface(frame) { ExpandedLayout(current, user, onNavigate, onLogout, content) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactLayout(
    current: AppRoute,
    user: Profile?,
    onNavigate: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val closeMenu = stringResource(Res.string.nav_close_menu)
    val close: () -> Unit = { scope.launch { drawerState.close() } }
    val shell = MaterialTheme.colorScheme.surface
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Escape closes the open menu, as any modal (the drawer of Material 3 does not handle it on the web).
        modifier =
            Modifier.onPreviewKeyEvent { event ->
                val isEscape = event.key == Key.Escape && event.type == KeyEventType.KeyDown
                if (isEscape && drawerState.isOpen) close()
                isEscape && drawerState.isOpen
            },
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = shell,
                modifier = Modifier.widthIn(max = SidebarWidth + 32.dp).semantics { paneTitle = closeMenu },
            ) {
                Sidebar(
                    current = current,
                    user = user,
                    onNavigate = {
                        close()
                        // The current page only closes the menu: navigating to it again keeps this drawer open.
                        if (it != current) onNavigate(it)
                    },
                    onLogout = {
                        close()
                        onLogout()
                    },
                    themeInList = true,
                )
            }
        },
    ) {
        Scaffold(
            containerColor = shell,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        TextButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.heightIn(min = MinTouchTarget).focusOutline(),
                        ) {
                            Text(stringResource(Res.string.nav_menu))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = shell),
                )
            },
        ) { padding ->
            PageContent(Modifier.padding(padding), horizontal = 16.dp, top = 8.dp, content = content)
        }
    }
}

@Composable
private fun MediumLayout(
    current: AppRoute,
    isShort: Boolean,
    onNavigate: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    val navigationName = stringResource(Res.string.nav_main)
    val extended = LocalExtendedColors.current
    val scheme = MaterialTheme.colorScheme
    val itemColors =
        NavigationRailItemDefaults.colors(
            selectedIconColor = extended.onHero,
            selectedTextColor = scheme.onSurface,
            indicatorColor = extended.hero,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant,
        )
    ShellSurface(outerPadding = if (isShort) 0.dp else 12.dp) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = Color.Transparent,
                modifier = Modifier.semantics { contentDescription = navigationName },
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                    navigationItems.forEach { item ->
                        val label = stringResource(item.label)
                        val isCurrent = item.route.isSameDestination(current)
                        NavigationRailItem(
                            selected = isCurrent,
                            onClick = { onNavigate(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(label) },
                            alwaysShowLabel = true,
                            colors = itemColors,
                            modifier = Modifier.focusOutline().currentPage(label, isCurrent),
                        )
                    }
                    ThemeRailSwitch()
                    NavigationRailItem(
                        selected = false,
                        onClick = onLogout,
                        icon = { Icon(AppIcons.Logout, contentDescription = null) },
                        label = { Text(stringResource(Res.string.nav_logout)) },
                        alwaysShowLabel = true,
                        colors = itemColors,
                        modifier = Modifier.focusOutline(),
                    )
                }
            }
            PageContent(
                Modifier.weight(1f),
                horizontal = if (isShort) 16.dp else 20.dp,
                top = if (isShort) 8.dp else 20.dp,
                content = content,
            )
        }
    }
}

@Composable
private fun ExpandedLayout(
    current: AppRoute,
    user: Profile?,
    onNavigate: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Sidebar(current, user, onNavigate, onLogout, modifier = Modifier.width(SidebarWidth).fillMaxHeight())
        PageContent(
            Modifier.weight(1f),
            horizontal = 28.dp,
            top = if (LocalDenseLayout.current) 8.dp else 24.dp,
            content = content,
        )
    }
}

/** The rounded shell that holds the navigation and the page, on the tinted ground. */
@Composable
private fun ShellSurface(
    outerPadding: Dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize().padding(outerPadding),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        content = content,
    )
}

/**
 * Brand, the destinations (scrolling when they do not fit), the theme switch, the user and "Sair". No heading: the
 * h1 is the page's. The theme switch sits above the user; in the compact menu ([themeInList]) it goes at the end of
 * the scrolling destinations instead, so that on a short phone it never takes the room of a destination.
 */
@Composable
private fun Sidebar(
    current: AppRoute,
    user: Profile?,
    onNavigate: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    themeInList: Boolean = false,
) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
        Brand()
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            navigationItems.forEach { item ->
                val label = stringResource(item.label)
                val isCurrent = item.route.isSameDestination(current)
                SidebarItem(
                    label = label,
                    icon = item.icon,
                    selected = isCurrent,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.currentPage(label, isCurrent),
                )
            }
            if (themeInList) ThemeSegmentedSwitch(Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(12.dp))
        if (!themeInList) {
            ThemeSegmentedSwitch()
            Spacer(Modifier.height(12.dp))
        }
        if (user != null) {
            UserCard(user)
            Spacer(Modifier.height(6.dp))
        }
        SidebarItem(
            label = stringResource(Res.string.nav_logout),
            icon = AppIcons.Logout,
            selected = false,
            onClick = onLogout,
        )
    }
}

@Composable
private fun Brand() {
    val extended = LocalExtendedColors.current
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(BrandMarkSize).background(extended.hero, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Bitcoin, contentDescription = null, tint = extended.onHero)
        }
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = LocalExtendedColors.current
    val scheme = MaterialTheme.colorScheme
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors =
            NavigationDrawerItemDefaults.colors(
                selectedContainerColor = extended.hero,
                selectedIconColor = extended.onHero,
                selectedTextColor = extended.onHero,
                unselectedContainerColor = Color.Transparent,
                unselectedIconColor = scheme.onSurfaceVariant,
                unselectedTextColor = scheme.onSurfaceVariant,
            ),
        modifier = Modifier.focusOutline(if (selected) scheme.onSurface else Color.Unspecified).then(modifier),
    )
}

/** Who is signed in: initials, name and e-mail, as plain text. */
@Composable
private fun UserCard(user: Profile) {
    val extended = LocalExtendedColors.current
    val name = user.name.trim()
    Surface(shape = MaterialTheme.shapes.medium, color = extended.card, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val initials = initials(name.ifEmpty { user.email })
            if (initials.isNotEmpty()) {
                Box(
                    modifier = Modifier.size(AvatarSize).background(extended.tint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials, style = MaterialTheme.typography.labelLarge)
                }
            }
            Column {
                if (name.isNotEmpty()) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "Maria Souza" → "MS"; a single word gives one letter. */
private fun initials(name: String): String {
    val words = name.split(' ').filter { it.isNotEmpty() }
    val letters = listOfNotNull(words.firstOrNull(), words.drop(1).lastOrNull()).map { it.take(1) }
    return letters.joinToString("").uppercase()
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
    horizontal: Dp,
    top: Dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // No scroll here: each screen scrolls itself (a LazyColumn cannot live inside a verticalScroll) and adds
        // the space below its last block, so a list reaches the bottom edge of the shell.
        Box(
            modifier =
                Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(start = horizontal, end = horizontal, top = top),
        ) {
            content()
        }
    }
}
