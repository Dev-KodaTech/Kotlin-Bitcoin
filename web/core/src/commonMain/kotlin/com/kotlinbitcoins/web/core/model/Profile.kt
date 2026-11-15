package com.kotlinbitcoins.web.core.model

/** Profile of the authenticated customer (`GET /me`, FR-005): only name and e-mail. */
data class Profile(
    val name: String,
    val email: String,
) {
    /** First word of `name.trim()`, for the greeting; null when the name is blank (greeting without name). */
    val firstName: String?
        get() =
            name
                .trim()
                .split(WHITESPACE)
                .first()
                .takeIf { it.isNotEmpty() }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
