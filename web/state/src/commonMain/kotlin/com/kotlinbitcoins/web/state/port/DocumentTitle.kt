package com.kotlinbitcoins.web.state.port

/** `document.title` of the current page (FR-046, contracts/ui-routes.md). */
fun interface DocumentTitle {
    fun set(text: String)
}
