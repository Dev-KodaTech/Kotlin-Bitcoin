package com.kotlinbitcoins.web.data.gateway

import com.kotlinbitcoins.web.core.error.ApiError
import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.model.Quote
import com.kotlinbitcoins.web.data.dto.QuoteDto
import com.kotlinbitcoins.web.data.http.ApiCall
import com.kotlinbitcoins.web.data.mapper.QuoteMapper
import com.kotlinbitcoins.web.state.port.QuoteGateway
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** [QuoteGateway] over `GET /quotes/current`; the prices keep the scale of the source. */
class HttpQuoteGateway(
    private val apiCall: ApiCall,
) : QuoteGateway {
    override suspend fun current(): Outcome<ApiError, Quote> =
        apiCall.read(QuoteDto.serializer(), QuoteMapper::toQuote) {
            method = HttpMethod.Get
            url("quotes/current")
        }
}
