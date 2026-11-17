package com.kotlinbitcoins.web.data.browser

import com.kotlinbitcoins.web.state.port.DocumentTitle

/** `document.title` of the current page (FR-046). */
class BrowserDocumentTitle : DocumentTitle {
    override fun set(text: String) = setDocumentTitle(text)
}
