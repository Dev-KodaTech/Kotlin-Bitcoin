package com.kotlinbitcoins.web.core.error

/** Why a typed amount was refused (W-11). Every case shows the INVALID_AMOUNT message (FR-040). */
sealed interface AmountInputError {
    data object Empty : AmountInputError

    data object InvalidFormat : AmountInputError

    data object TooManyDecimals : AmountInputError

    data object NotPositive : AmountInputError
}
