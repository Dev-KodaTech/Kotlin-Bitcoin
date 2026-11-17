package com.kotlinbitcoins.web.data.mapper

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.error.flatMap
import com.kotlinbitcoins.web.core.error.map
import com.kotlinbitcoins.web.core.model.EntryType
import com.kotlinbitcoins.web.core.model.Statement
import com.kotlinbitcoins.web.core.model.StatementEntry
import com.kotlinbitcoins.web.core.money.BtcAmount
import com.kotlinbitcoins.web.core.money.Money
import com.kotlinbitcoins.web.core.money.QuotePrice
import com.kotlinbitcoins.web.core.time.ApiInstant
import com.kotlinbitcoins.web.data.dto.StatementDto
import com.kotlinbitcoins.web.data.dto.StatementEntryDto

/**
 * [StatementDto] → [Statement], keeping the order of the API (newest first). Any value outside the contract (an
 * unknown `type` included) makes the whole statement [ApiError.Unexpected] (FR-041).
 */
object StatementMapper {
    fun toStatement(dto: StatementDto): Outcome<ApiError, Statement> =
        ApiInstant.parseApi(dto.from).flatMap { from ->
            ApiInstant.parseApi(dto.to).flatMap { to ->
                entries(dto.entries).map { Statement(from, to, it) }
            }
        }

    private fun entries(dtos: List<StatementEntryDto>): Outcome<ApiError, List<StatementEntry>> {
        val mapped = ArrayList<StatementEntry>(dtos.size)
        for (dto in dtos) {
            when (val entry = toEntry(dto)) {
                is Outcome.Success -> mapped += entry.value
                is Outcome.Failure -> return entry
            }
        }
        return Outcome.Success(mapped)
    }

    private fun toEntry(dto: StatementEntryDto): Outcome<ApiError, StatementEntry> =
        type(dto.type).flatMap { type ->
            Money.parseApi(dto.amount).flatMap { amount ->
                optional(dto.btcAmount, BtcAmount::parseApi).flatMap { btc ->
                    optional(dto.quote, QuotePrice::parseApi).flatMap { quote ->
                        ApiInstant.parseApi(dto.occurredAt).map { occurredAt ->
                            StatementEntry(
                                id = dto.id,
                                type = type,
                                amount = amount,
                                btcAmount = btc,
                                quote = quote,
                                investmentId = dto.investmentId,
                                requestId = dto.requestId,
                                occurredAt = occurredAt,
                            )
                        }
                    }
                }
            }
        }

    private fun type(raw: String): Outcome<ApiError, EntryType> =
        when (raw) {
            "DEPOSIT" -> Outcome.Success(EntryType.Deposit)
            "PURCHASE" -> Outcome.Success(EntryType.Purchase)
            "REDEMPTION" -> Outcome.Success(EntryType.Redemption)
            "REINVESTMENT" -> Outcome.Success(EntryType.Reinvestment)
            else -> Outcome.Failure(ApiError.Unexpected)
        }

    private fun <T> optional(
        raw: String?,
        parse: (String) -> Outcome<ApiError, T>,
    ): Outcome<ApiError, T?> = raw?.let(parse) ?: Outcome.Success(null)
}
