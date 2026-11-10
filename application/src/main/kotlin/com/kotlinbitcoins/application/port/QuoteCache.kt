package com.kotlinbitcoins.application.port

import com.kotlinbitcoins.domain.quote.Quote

/**
 * Cache of the latest quote, shared by every replica. Best-effort (R-06): it is the only port that does not return
 * an `Outcome`. A cache failure behaves as an empty cache, so [get] answers `null` and [put] is silently dropped
 * (the adapter only logs it).
 */
interface QuoteCache {
    fun get(): Quote?

    fun put(quote: Quote)
}
