package com.kotlinbitcoins.application.investment

import com.kotlinbitcoins.application.investment.PurchaseError.AccountNotFound
import com.kotlinbitcoins.application.investment.PurchaseError.InsufficientBalance
import com.kotlinbitcoins.application.investment.PurchaseError.InvalidAmount
import com.kotlinbitcoins.application.investment.PurchaseError.QuoteUnavailable
import com.kotlinbitcoins.application.investment.PurchaseError.ServiceUnavailable
import com.kotlinbitcoins.application.notification.NotificationEvent
import com.kotlinbitcoins.application.notification.NotificationType
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InvestmentRepository
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.application.quote.QuoteService
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.investment.BtcConversion
import com.kotlinbitcoins.domain.investment.Investment
import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.kotlinbitcoins.domain.shared.TransactionId
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.domain.shared.mapError
import java.time.Instant
import java.util.UUID

data class PurchaseCommand(
    val accountId: AccountId,
    val amount: PositiveMoney,
)

data class PurchaseReceipt(
    val investmentId: InvestmentId,
    val transactionId: TransactionId,
    val amount: Money,
    val btcAmount: BtcAmount,
    val quote: QuotePrice,
    val purchasedAt: Instant,
    val balance: Money,
)

/**
 * Buys bitcoin with an amount in reais at the platform's sell price (US5, FR-013..FR-015). The account row is
 * locked first (R-05), so concurrent purchases on the same account run one after the other and the balance is never
 * overdrawn (FR-010, FR-034). A single quote is obtained per purchase, and that same quote is the one recorded.
 * Balance, investment, statement line and notification event are written in one transaction: either all of them
 * exist or none does. The e-mail is sent later from the outbox, so its failure never undoes the purchase (FR-031).
 * It takes one port per concern: account, investment, statement, outbox, quote, transaction and clock.
 */
@Suppress("LongParameterList")
class PurchaseBitcoin(
    private val accounts: AccountRepository,
    private val investments: InvestmentRepository,
    private val transactions: TransactionRepository,
    private val notifications: NotificationPublisher,
    private val quoteService: QuoteService,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(command: PurchaseCommand): Outcome<PurchaseError, PurchaseReceipt> =
        transactionRunner.run<PurchaseError, PurchaseReceipt>({ ServiceUnavailable }) { purchase(command) }

    private fun purchase(command: PurchaseCommand): Outcome<PurchaseError, PurchaseReceipt> =
        lock(command.accountId).flatMap { account ->
            quote().flatMap { quote ->
                BtcConversion
                    .purchase(command.amount, quote.sellPrice)
                    .mapError<_, _, PurchaseError> { InvalidAmount }
                    .flatMap { btc ->
                        debit(account, command.amount).flatMap { debited ->
                            settle(debited, command, quote, btc)
                        }
                    }
            }
        }

    private fun settle(
        debited: Account,
        command: PurchaseCommand,
        quote: Quote,
        btc: BtcAmount,
    ): Outcome<PurchaseError, PurchaseReceipt> {
        val now = clock.now()
        return nextFifoSequence().flatMap { fifoSequence ->
            val investment =
                Investment.purchase(debited.id, now, fifoSequence, command.amount.money, quote.sellPrice, btc)
            val line =
                Transaction.purchase(debited.id, command.amount, btc, quote.sellPrice, investment.id, now)
            saveAccount(debited)
                .flatMap { saveInvestment(investment) }
                .flatMap { record(line) }
                .flatMap { publish(purchaseConfirmed(debited, command.amount, btc, now)) }
                .map {
                    PurchaseReceipt(
                        investmentId = investment.id,
                        transactionId = line.id,
                        amount = command.amount.money,
                        btcAmount = btc,
                        quote = quote.sellPrice,
                        purchasedAt = now,
                        balance = debited.balance,
                    )
                }
        }
    }

    private fun lock(accountId: AccountId): Outcome<PurchaseError, Account> =
        accounts
            .findByIdForUpdate(accountId)
            .mapError<_, _, PurchaseError> { ServiceUnavailable }
            .flatMap { account -> if (account == null) Outcome.Failure(AccountNotFound) else Outcome.Success(account) }

    private fun quote(): Outcome<PurchaseError, Quote> = quoteService.currentQuote().mapError { QuoteUnavailable }

    private fun debit(
        account: Account,
        amount: PositiveMoney,
    ): Outcome<PurchaseError, Account> = account.debit(amount).mapError { InsufficientBalance }

    private fun nextFifoSequence(): Outcome<PurchaseError, Long> =
        investments.nextFifoSequence().mapError { ServiceUnavailable }

    private fun saveAccount(account: Account): Outcome<PurchaseError, Unit> =
        accounts.save(account).mapError<_, _, PurchaseError> { ServiceUnavailable }.map { }

    private fun saveInvestment(investment: Investment): Outcome<PurchaseError, Unit> =
        investments.save(investment).mapError<_, _, PurchaseError> { ServiceUnavailable }.map { }

    private fun record(line: Transaction): Outcome<PurchaseError, Unit> =
        transactions.saveAll(listOf(line)).mapError { ServiceUnavailable }

    private fun publish(event: NotificationEvent): Outcome<PurchaseError, Unit> =
        notifications.publish(event).mapError { ServiceUnavailable }

    private fun purchaseConfirmed(
        account: Account,
        amount: PositiveMoney,
        btcAmount: BtcAmount,
        now: Instant,
    ) = NotificationEvent(
        id = UUID.randomUUID(),
        type = NotificationType.PURCHASE_CONFIRMED,
        accountId = account.id,
        recipientEmail = account.email,
        recipientName = account.name,
        amount = amount.money,
        createdAt = now,
        btcAmount = btcAmount,
    )
}
