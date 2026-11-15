package com.kotlinbitcoins.web.data.dto

import kotlinx.serialization.Serializable

/** `ProfileResponse` of the contract (`GET /me`). */
@Serializable
data class ProfileDto(
    val name: String,
    val email: String,
)
