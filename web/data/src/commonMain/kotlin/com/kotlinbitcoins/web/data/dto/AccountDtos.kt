package com.kotlinbitcoins.web.data.dto

import kotlinx.serialization.Serializable

/** `RegisterAccountRequest` of the contract. */
@Serializable
data class RegisterAccountRequestDto(
    val name: String,
    val email: String,
    val password: String,
) {
    override fun toString(): String = "RegisterAccountRequestDto(name=$name, email=$email, password=****)"
}

/** `AccountResponse` of the contract; `balance` and `createdAt` are not used by the client. */
@Serializable
data class AccountResponseDto(
    val id: String,
    val name: String,
    val email: String,
)

/** `LoginRequest` of the contract. */
@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
) {
    override fun toString(): String = "LoginRequestDto(email=$email, password=****)"
}

/** `LoginResponse` of the contract; `expiresIn` in seconds. */
@Serializable
data class LoginResponseDto(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
) {
    override fun toString(): String = "LoginResponseDto(****, tokenType=$tokenType, expiresIn=$expiresIn)"
}
