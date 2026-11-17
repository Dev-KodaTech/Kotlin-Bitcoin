package com.kotlinbitcoins.web.state.support

import com.kotlinbitcoins.web.state.port.DocumentTitle

class FakeDocumentTitle : DocumentTitle {
    val titles = mutableListOf<String>()

    val current: String? get() = titles.lastOrNull()

    override fun set(text: String) {
        titles += text
    }
}
