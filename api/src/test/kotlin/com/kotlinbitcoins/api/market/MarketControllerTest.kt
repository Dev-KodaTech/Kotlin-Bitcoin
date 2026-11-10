package com.kotlinbitcoins.api.market

import com.kotlinbitcoins.api.support.WebMvcTestConfiguration
import com.kotlinbitcoins.application.ledger.DailyVolume
import com.kotlinbitcoins.application.ledger.DailyVolumeError
import com.kotlinbitcoins.application.ledger.GetDailyVolume
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Outcome
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate

@WebMvcTest(controllers = [MarketController::class])
@Import(WebMvcTestConfiguration::class)
class MarketControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var getDailyVolume: GetDailyVolume

    @Test
    fun `answers 200 with the date and the totals as 8-place strings and nothing that identifies a client`() {
        every { getDailyVolume.execute() } returns
            Outcome.Success(DailyVolume(LocalDate.parse("2026-11-09"), BtcAmount(300_000), BtcAmount(50_000)))

        mockMvc
            .get("/api/v1/market/daily-volume") { with(jwt()) }
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.date") { value("2026-11-09") }
                jsonPath("$.boughtBtc") { value("0.00300000") }
                jsonPath("$.soldBtc") { value("0.00050000") }
                jsonPath("$.length()") { value(3) }
            }
    }

    @Test
    fun `without operations the totals are zero with 8 places`() {
        every { getDailyVolume.execute() } returns
            Outcome.Success(DailyVolume(LocalDate.parse("2026-11-09"), BtcAmount.ZERO, BtcAmount.ZERO))

        mockMvc
            .get("/api/v1/market/daily-volume") { with(jwt()) }
            .andExpect {
                status { isOk() }
                jsonPath("$.boughtBtc") { value("0.00000000") }
                jsonPath("$.soldBtc") { value("0.00000000") }
            }
    }

    @Test
    fun `an infrastructure failure is 503 SERVICE_UNAVAILABLE`() {
        every { getDailyVolume.execute() } returns Outcome.Failure(DailyVolumeError.ServiceUnavailable)

        mockMvc
            .get("/api/v1/market/daily-volume") { with(jwt()) }
            .andExpect {
                status { isServiceUnavailable() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
            }
    }
}
