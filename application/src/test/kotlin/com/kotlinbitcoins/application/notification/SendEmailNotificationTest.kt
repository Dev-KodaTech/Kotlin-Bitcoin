package com.kotlinbitcoins.application.notification

import com.kotlinbitcoins.application.port.EmailContent
import com.kotlinbitcoins.application.port.EmailMessage
import com.kotlinbitcoins.application.port.EmailSendError
import com.kotlinbitcoins.application.port.EmailSender
import com.kotlinbitcoins.domain.account.Email
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class SendEmailNotificationTest {
    private val emailSender = mockk<EmailSender>()
    private val sendEmailNotification = SendEmailNotification(emailSender)

    @Test
    fun `DEPOSIT_CONFIRMED becomes a deposit e-mail to the recipient`() {
        val message = slot<EmailMessage>()
        every { emailSender.send(capture(message)) } returns Outcome.Success(Unit)

        val result = sendEmailNotification.execute(event())

        assertThat(result).isEqualTo(Outcome.Success(Unit))
        assertThat(message.captured.recipientEmail.value).isEqualTo("ana@example.com")
        assertThat(message.captured.recipientName).isEqualTo("Ana Souza")
        assertThat(message.captured.content).isEqualTo(EmailContent.DepositConfirmed(money("100.00")))
    }

    @Test
    fun `PURCHASE_CONFIRMED becomes a purchase e-mail with the amount in reais and the bitcoin bought`() {
        val message = slot<EmailMessage>()
        every { emailSender.send(capture(message)) } returns Outcome.Success(Unit)

        val result =
            sendEmailNotification.execute(
                event().copy(
                    type = NotificationType.PURCHASE_CONFIRMED,
                    amount = money("400.00"),
                    btcAmount = BtcAmount(100_000),
                ),
            )

        assertThat(result).isEqualTo(Outcome.Success(Unit))
        assertThat(message.captured.content)
            .isEqualTo(EmailContent.PurchaseConfirmed(money("400.00"), BtcAmount(100_000)))
    }

    @Test
    fun `REDEMPTION_CONFIRMED becomes a redemption e-mail with the amount in reais and the bitcoin sold`() {
        val message = slot<EmailMessage>()
        every { emailSender.send(capture(message)) } returns Outcome.Success(Unit)

        val result =
            sendEmailNotification.execute(
                event().copy(
                    type = NotificationType.REDEMPTION_CONFIRMED,
                    amount = money("250.00"),
                    btcAmount = BtcAmount(125_000),
                ),
            )

        assertThat(result).isEqualTo(Outcome.Success(Unit))
        assertThat(message.captured.content)
            .isEqualTo(EmailContent.RedemptionConfirmed(money("250.00"), BtcAmount(125_000)))
    }

    @Test
    fun `a sender failure comes back as a value, never as an exception`() {
        every { emailSender.send(any()) } returns Outcome.Failure(EmailSendError("provider rejected"))

        val result = sendEmailNotification.execute(event())

        assertThat(result).isEqualTo(Outcome.Failure(EmailSendError("provider rejected")))
    }

    private fun event() =
        NotificationEvent(
            id = UUID.randomUUID(),
            type = NotificationType.DEPOSIT_CONFIRMED,
            accountId = AccountId.random(),
            recipientEmail = (Email.of("ana@example.com") as Outcome.Success).value,
            recipientName = "Ana Souza",
            amount = money("100.00"),
            createdAt = Instant.parse("2026-11-09T11:10:00Z"),
        )

    private fun money(value: String): Money = (Money.of(BigDecimal(value)) as Outcome.Success).value
}
