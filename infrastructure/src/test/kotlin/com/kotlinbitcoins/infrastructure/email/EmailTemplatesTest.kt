package com.kotlinbitcoins.infrastructure.email

import com.kotlinbitcoins.application.port.EmailContent
import com.kotlinbitcoins.application.port.EmailMessage
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.infrastructure.support.AccountFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EmailTemplatesTest {
    @Test
    fun `purchase e-mail states the amount in reais and the bitcoin bought in Portuguese`() {
        val rendered =
            EmailTemplates.render(
                EmailMessage(
                    recipientEmail = AccountFixtures.email("ana@example.com"),
                    recipientName = "Ana Souza",
                    content = EmailContent.PurchaseConfirmed(AccountFixtures.money("400.00"), BtcAmount(100_000)),
                ),
            )

        assertThat(rendered.subject).contains("R$ 400,00")
        assertThat(rendered.body)
            .contains("Ana Souza")
            .contains("Você investiu R$ 400,00 e comprou 0,00100000 BTC.")
    }

    @Test
    fun `redemption e-mail states the amount redeemed in reais and the bitcoin sold in Portuguese`() {
        val rendered =
            EmailTemplates.render(
                EmailMessage(
                    recipientEmail = AccountFixtures.email("ana@example.com"),
                    recipientName = "Ana Souza",
                    content = EmailContent.RedemptionConfirmed(AccountFixtures.money("1250.00"), BtcAmount(125_000)),
                ),
            )

        assertThat(rendered.subject).contains("R$ 1.250,00")
        assertThat(rendered.body)
            .contains("Ana Souza")
            .contains("Você resgatou R$ 1.250,00 e vendeu 0,00125000 BTC.")
    }
}
