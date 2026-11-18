package com.kotlinbitcoins.web.ui.shell

import com.kotlinbitcoins.web.core.model.Profile
import com.kotlinbitcoins.web.state.port.Announcer
import com.kotlinbitcoins.web.state.port.DocumentTitle

/**
 * Ports used by the navigation itself: page title (FR-046), the live regions (FR-047) and who is signed in, for
 * the user block of the sidebar ([profile] is null when `GET /me` fails: the block is simply not shown).
 */
class ShellPorts(
    val documentTitle: DocumentTitle,
    val announcer: Announcer,
    val profile: suspend () -> Profile? = { null },
)
