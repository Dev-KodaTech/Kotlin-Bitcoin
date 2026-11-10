package com.kotlinbitcoins.application.port

import com.kotlinbitcoins.domain.shared.AccountId

/** Access token handed to the client after login. [toString] never prints the token itself (FR-007). */
data class AccessToken(
    val value: String,
    val expiresInSeconds: Long,
) {
    override fun toString(): String = "AccessToken(value=****, expiresInSeconds=$expiresInSeconds)"
}

/** Issues access tokens whose subject is the account (R-09). */
fun interface TokenIssuer {
    fun issue(accountId: AccountId): AccessToken
}
