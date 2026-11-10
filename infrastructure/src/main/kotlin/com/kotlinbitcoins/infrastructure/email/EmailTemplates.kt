package com.kotlinbitcoins.infrastructure.email

import com.kotlinbitcoins.application.port.EmailContent
import com.kotlinbitcoins.application.port.EmailMessage
import com.kotlinbitcoins.domain.shared.BtcAmount
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

            is EmailContent.PurchaseConfirmed -> {
                val amount = reais(content.amount)
                val btc = btc(content.btcAmount)
                RenderedEmail(
                    subject = "Compra confirmada: $amount",
                    body =
                        "Olá, ${message.recipientName}!\n\n" +
                            "Você investiu $amount e comprou $btc BTC.\n\n" +
                            "Equipe KotlinBitcoins",
                )
            }
        }

    /** Bitcoin with 8 decimals and a decimal comma, e.g. `0,00100000`, from the exact satoshi count. */
    private fun btc(value: BtcAmount): String =
        DecimalFormat("0.00000000", DecimalFormatSymbols(PT_BR)).format(value.toBtcDecimal())

    /** Formats without going through `double`, so the cents are exactly the ones in [value]. */
    private fun reais(value: Money): String =
        "R$ " + DecimalFormat("#,##0.00", DecimalFormatSymbols(PT_BR)).format(value.amount)
}
