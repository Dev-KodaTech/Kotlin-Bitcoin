package com.kotlinbitcoins.web.core.model

import kotlin.time.Duration

/** Token of `POST /auth/login` (200). The token is never logged nor shown (FR-008). */
data class LoginResult(
    val accessToken: String,
    val expiresIn: Duration,
) {
    override fun toString(): String = "LoginResult(****, expiresIn=$expiresIn)"
}
