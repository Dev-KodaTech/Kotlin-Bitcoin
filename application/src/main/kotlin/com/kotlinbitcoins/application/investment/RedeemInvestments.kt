package com.kotlinbitcoins.application.investment

import com.kotlinbitcoins.application.investment.RedemptionError.AccountNotFound
import com.kotlinbitcoins.application.investment.RedemptionError.QuoteUnavailable
import com.kotlinbitcoins.application.investment.RedemptionError.RedemptionExceedsAvailable
import com.kotlinbitcoins.application.investment.RedemptionError.ServiceUnavailable
import com.kotlinbitcoins.application.notification.NotificationEvent
import com.kotlinbitcoins.application.notification.NotificationType
import com.kotlinbitcoins.application.port.AccountRepository
import com.kotlinbitcoins.application.port.Clock
import com.kotlinbitcoins.application.port.InfrastructureFailure
import com.kotlinbitcoins.application.port.InvestmentRepository
import com.kotlinbitcoins.application.port.NotificationPublisher
import com.kotlinbitcoins.application.port.TransactionRepository
import com.kotlinbitcoins.application.port.TransactionRunner
import com.kotlinbitcoins.application.quote.QuoteService
import com.kotlinbitcoins.domain.account.Account
import com.kotlinbitcoins.domain.investment.Investment
import com.kotlinbitcoins.domain.investment.Liquidation
import com.kotlinbitcoins.domain.investment.RedemptionPlan
import com.kotlinbitcoins.domain.investment.RedemptionPlanner
import com.kotlinbitcoins.domain.ledger.Transaction
import com.kotlinbitcoins.domain.quote.Quote
import com.kotlinbitcoins.domain.shared.AccountId
import com.kotlinbitcoins.domain.shared.BtcAmount
import com.kotlinbitcoins.domain.shared.InvestmentId
import com.kotlinbitcoins.domain.shared.Money
import com.kotlinbitcoins.domain.shared.Outcome
import com.kotlinbitcoins.domain.shared.PositiveMoney
import com.kotlinbitcoins.domain.shared.QuotePrice
import com.kotlinbitcoins.domain.shared.RedemptionRequestId
import com.kotlinbitcoins.domain.shared.flatMap
import com.kotlinbitcoins.domain.shared.map
import com.kotlinbitcoins.domain.shared.mapError
import java.time.Instant
import java.util.UUID

data class RedeemCommand(
    val accountId: AccountId,
    val amount: PositiveMoney,
)

/** One investment liquidated by the redemption, as reported to the client (FR-019). */
data class LiquidationReceipt(
    val investmentId: InvestmentId,
    val amount: Money,
    val btcAmount: BtcAmount,
    val isPartial: Boolean,
)

/** The investment created from the residue of a partial liquidation (FR-020). */
data class ReinvestmentReceipt(
    val investmentId: InvestmentId,
    val btcAmount: BtcAmount,
    val investedAmount: Money,
    val purchaseQuote: QuotePrice,
    val purchasedAt: Instant,
)

data class RedemptionReceipt(
    val requestId: RedemptionRequestId,
    val amount: Money,
    val btcSold: BtcAmount,
    val quote: QuotePrice,
    val balance: Money,
    val occurredAt: Instant,
    val liquidations: List<LiquidationReceipt>,
    val reinvestment: ReinvestmentReceipt?,
)

/**
 * Redeems an amount in reais from the active investments (US7, FR-018..FR-024). The account row is locked first
 * (R-05), so concurrent redemptions and purchases on the same account run one after the other and no satoshi is
 * liquidated twice (FR-034). One quote is obtained and its buy price values every investment. The
 * [RedemptionPlanner] decides what is liquidated; this use case applies the plan: it closes the liquidated
 * investments, creates the reinvestment of the residue, credits exactly the amount requested, writes one statement
 * line per liquidation plus one for the reinvestment under the same request id (FR-024a) and registers the e-mail
 * event. All of it is one transaction: either everything exists or nothing does.
 */
@Suppress("LongParameterList")
class RedeemInvestments(
    private val accounts: AccountRepository,
    private val investments: InvestmentRepository,
    private val transactions: TransactionRepository,
    private val notifications: NotificationPublisher,
    private val quoteService: QuoteService,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(command: RedeemCommand): Outcome<RedemptionError, RedemptionReceipt> =
        transactionRunner.run<RedemptionError, RedemptionReceipt>({ ServiceUnavailable }) { redeem(command) }

    private fun redeem(command: RedeemCommand): Outcome<RedemptionError, RedemptionReceipt> =
        lock(command.accountId).flatMap { account ->
            quote().flatMap { quote ->
                active(account.id).flatMap { active ->
                    RedemptionPlanner
                        .plan(command.amount, active, quote.buyPrice)
                        .mapError<_, _, RedemptionError> { RedemptionExceedsAvailable }
                        .flatMap { plan -> settle(account, command.amount, quote, plan) }
                }
            }
        }

    private fun settle(
        account: Account,
        amount: PositiveMoney,
        quote: Quote,
        plan: RedemptionPlan,
    ): Outcome<RedemptionError, RedemptionReceipt> {
        val now = clock.now()
        val requestId = RedemptionRequestId.random()
        val reinvestment = plan.residual?.let { Investment.reinvestmentOf(it.origin, it.sats) }
        val credited = account.credit(amount.money)
        val lines = statementLines(account.id, plan, quote.buyPrice, reinvestment, requestId, now)
        return plan.liquidations
            .map { it.investment.close(now) }
            .fold(Outcome.Success(Unit) as Outcome<RedemptionError, Unit>) { done, closed ->
                done.flatMap { close(closed) }
            }.flatMap { reinvestment?.let(::saveInvestment) ?: Outcome.Success(Unit) }
            .flatMap { saveAccount(credited) }
            .flatMap { record(lines) }
            .flatMap { publish(redemptionConfirmed(credited, amount.money, plan.totalSoldSats, now)) }
            .map { receipt(plan, requestId, amount.money, quote.buyPrice, credited, now, reinvestment) }
    }

    private fun statementLines(
        accountId: AccountId,
        plan: RedemptionPlan,
        buyPrice: QuotePrice,
        reinvestment: Investment?,
        requestId: RedemptionRequestId,
        now: Instant,
    ): List<Transaction> {
        val redemptions =
            plan.liquidations.map {
                Transaction.redemption(
                    accountId,
                    it.credit,
                    it.soldSats,
                    buyPrice,
                    it.investment.id,
                    requestId,
                    now,
                )
            }
        val reinvested =
            reinvestment?.let {
                Transaction.reinvestment(
                    accountId,
                    it.investedAmount,
                    it.btcAmount,
                    it.purchaseQuote,
                    it.id,
                    requestId,
                    now,
                )
            }
        return redemptions + listOfNotNull(reinvested)
    }

    private fun receipt(
        plan: RedemptionPlan,
        requestId: RedemptionRequestId,
        amount: Money,
        buyPrice: QuotePrice,
        credited: Account,
        now: Instant,
        reinvestment: Investment?,
    ) = RedemptionReceipt(
        requestId = requestId,
        amount = amount,
        btcSold = plan.totalSoldSats,
        quote = buyPrice,
        balance = credited.balance,
        occurredAt = now,
        liquidations = plan.liquidations.map(::liquidationReceipt),
        reinvestment =
            reinvestment?.let {
                ReinvestmentReceipt(it.id, it.btcAmount, it.investedAmount, it.purchaseQuote, it.purchasedAt)
            },
    )

    private fun liquidationReceipt(liquidation: Liquidation) =
        LiquidationReceipt(
            investmentId = liquidation.investment.id,
            amount = liquidation.credit,
            btcAmount = liquidation.soldSats,
            isPartial = liquidation.isPartial,
        )

    private fun lock(accountId: AccountId): Outcome<RedemptionError, Account> =
        accounts
            .findByIdForUpdate(accountId)
            .mapError<_, _, RedemptionError> { ServiceUnavailable }
            .flatMap { account -> if (account == null) Outcome.Failure(AccountNotFound) else Outcome.Success(account) }

    private fun quote(): Outcome<RedemptionError, Quote> = quoteService.currentQuote().mapError { QuoteUnavailable }

    private fun active(accountId: AccountId): Outcome<RedemptionError, List<Investment>> =
        investments.findActiveByAccountInFifoOrder(accountId).mapError { unavailable(it) }

    private fun close(investment: Investment): Outcome<RedemptionError, Unit> =
        investments.close(investment).mapError { unavailable(it) }.map { }

    private fun saveInvestment(investment: Investment): Outcome<RedemptionError, Unit> =
        investments.save(investment).mapError { unavailable(it) }.map { }

    private fun saveAccount(account: Account): Outcome<RedemptionError, Unit> =
        accounts.save(account).mapError<_, _, RedemptionError> { ServiceUnavailable }.map { }

    private fun record(lines: List<Transaction>): Outcome<RedemptionError, Unit> =
        transactions.saveAll(lines).mapError { unavailable(it) }

    private fun publish(event: NotificationEvent): Outcome<RedemptionError, Unit> =
        notifications.publish(event).mapError { unavailable(it) }

    // The failure's reason is for logs only; the client always gets the same generic error (FR-035).
    @Suppress("UnusedParameter")
    private fun unavailable(failure: InfrastructureFailure): RedemptionError = ServiceUnavailable

    private fun redemptionConfirmed(
        account: Account,
        amount: Money,
        btcSold: BtcAmount,
        now: Instant,
    ) = NotificationEvent(
        id = UUID.randomUUID(),
        type = NotificationType.REDEMPTION_CONFIRMED,
        accountId = account.id,
        recipientEmail = account.email,
        recipientName = account.name,
        amount = amount,
        createdAt = now,
        btcAmount = btcSold,
    )
}
