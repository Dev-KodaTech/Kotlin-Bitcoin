package com.kotlinbitcoins.api.account

import com.kotlinbitcoins.api.error.ProblemDetailsMapper
import com.kotlinbitcoins.api.openapi.PROBLEM_JSON
import com.kotlinbitcoins.api.openapi.ProblemBody
import com.kotlinbitcoins.api.web.CurrentAccount
import com.kotlinbitcoins.application.account.GetProfile
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.fold
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Profile of the authenticated account (FR-036): only name and e-mail. The account comes from the token, so there is
 * no way to ask for another customer (FR-033).
 */
@RestController
class ProfileController(
    private val getProfile: GetProfile,
    private val problems: ProblemDetailsMapper,
) {
    @ApiResponse(
        responseCode = "200",
        description = "Nome e e-mail da conta do token",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ProfileResponse::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente, malformado, adulterado ou expirado",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemBody::class))],
    )
    @GetMapping("/api/v1/me")
    fun profile(
        @CurrentAccount accountId: AccountId,
    ): ResponseEntity<*> =
        getProfile
            .execute(accountId)
            .fold(
                onFailure = problems::getProfileError,
                onSuccess = { ResponseEntity.ok(ProfileResponse.of(it)) },
            )
}
