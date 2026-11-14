package com.kotlinbitcoins.web.ui.shell

import com.kotlinbitcoins.web.state.port.Announcer
import com.kotlinbitcoins.web.state.port.DocumentTitle

/** Browser ports used by the navigation itself: page title (FR-046) and the live regions (FR-047). */
class ShellPorts(
    val documentTitle: DocumentTitle,
    val announcer: Announcer,
)
