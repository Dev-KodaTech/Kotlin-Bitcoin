package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.format.ApiFormats
import com.kotlinbitcoins.application.account.RegisteredAccount
import com.kotlinbitcoins.application.port.AccessToken
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Body of `POST /accounts`. Bean Validation only checks presence and length; the e-mail format and the 72-byte
 * password limit are checked once, by the domain and the use case, so each rule lives in a single place.
 */
data class RegisterAccountRequest(
    @field:NotBlank(message = "Nome é obrigatório.")
    @field:Size(max = 200, message = "Nome deve ter no máximo 200 caracteres.")
    val name: String?,
    @field:NotBlank(message = "E-mail é obrigatório.")
    @field:Size(max = 320, message = "E-mail deve ter no máximo 320 caracteres.")
    val email: String?,
    @field:NotBlank(message = "Senha é obrigatória.")
    @field:Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres.")
    @field:Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    val password: String?,
) {
    override fun toString(): String = "RegisterAccountRequest(name=$name, email=$email, password=****)"
}

data class AccountResponse(
    val id: String,
    val name: String,
    val email: String,
    val balance: String,
    val createdAt: String,
) {
    companion object {
        fun of(
            account: RegisteredAccount,
            formats: ApiFormats,
        ) = AccountResponse(
            id = account.id.toString(),
            name = account.name,
            email = account.email.value,
            balance = formats.money(account.balance),
            createdAt = formats.instant(account.createdAt),
        )
    }
}

data class LoginRequest(
    @field:NotBlank(message = "E-mail é obrigatório.")
    val email: String?,
    @field:NotBlank(message = "Senha é obrigatória.")
    @field:Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    val password: String?,
) {
    override fun toString(): String = "LoginRequest(email=$email, password=****)"
}

data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
) {
    override fun toString(): String = "LoginResponse(accessToken=****, tokenType=$tokenType, expiresIn=$expiresIn)"

    companion object {
        private const val BEARER = "Bearer"

        fun of(token: AccessToken) = LoginResponse(token.value, BEARER, token.expiresInSeconds)
    }
}
