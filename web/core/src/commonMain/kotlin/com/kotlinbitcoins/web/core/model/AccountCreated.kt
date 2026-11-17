package com.kotlinbitcoins.web.core.model

/** Account created by `POST /accounts` (201). The balance of the response is ignored: the dashboard reads it. */
data class AccountCreated(
    val id: String,
    val name: String,
    val email: String,
)
