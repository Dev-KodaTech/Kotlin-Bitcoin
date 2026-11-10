package com.kotlinbitcoins.infrastructure.email

import com.kotlinbitcoins.application.port.EmailContent
import com.kotlinbitcoins.application.port.EmailMessage
import com.kotlinbitcoins.domain.shared.Money
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class RenderedEmail(
    val subject: String,
    val body: String,
)

/** Subject and body, in Portuguese, of each kind of e-mail. Amounts read `R$ 1.000,00`. */
object EmailTemplates {
    private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

    fun render(message: EmailMessage): RenderedEmail =
        when (val content = message.content) {
            is EmailContent.DepositConfirmed -> {
                val amount = reais(content.amount)
                RenderedEmail(
                    subject = "Depósito confirmado: $amount",
                    body =
                        "Olá, ${message.recipientName}!\n\n" +
                            "Seu depósito de $amount foi confirmado e já está disponível no seu saldo.\n\n" +
                            "Equipe KotlinBitcoins",
                )
            }
        }

    /** Formats without going through `double`, so the cents are exactly the ones in [value]. */
    private fun reais(value: Money): String =
        "R$ " + DecimalFormat("#,##0.00", DecimalFormatSymbols(PT_BR)).format(value.amount)
}
