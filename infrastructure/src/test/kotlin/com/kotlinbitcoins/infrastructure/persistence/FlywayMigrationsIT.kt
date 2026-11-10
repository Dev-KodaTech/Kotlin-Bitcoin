package com.kotlinbitcoins.infrastructure.persistence

import com.kotlinbitcoins.infrastructure.support.PostgresIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class FlywayMigrationsIT : PostgresIntegrationTest() {
    private val now = Timestamp.from(Instant.parse("2026-11-09T11:10:00Z"))

    @Test
    fun `applies V1 to V6 on an empty database and the context starts with Hibernate validation`() {
        val versions =
            jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String::class.java,
            )

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6")
    }

    @Test
    fun `balance can never be negative`() {
        val accountId = insertAccount()

        assertThatThrownBy {
            jdbcTemplate.update("UPDATE accounts SET balance = -0.01 WHERE id = ?", accountId)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `deposit transaction cannot carry BTC amount`() {
        val accountId = insertAccount()

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO transactions (id, account_id, type, amount, btc_amount_sats, quote, occurred_at)
                VALUES (?, ?, 'DEPOSIT', 100.00, 1000, NULL, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                accountId,
                now,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `deposit transaction without BTC data is accepted`() {
        val accountId = insertAccount()

        val inserted =
            jdbcTemplate.update(
                """
                INSERT INTO transactions (id, account_id, type, amount, occurred_at)
                VALUES (?, ?, 'DEPOSIT', 100.00, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                accountId,
                now,
            )

        assertThat(inserted).isEqualTo(1)
    }

    @Test
    fun `investment must hold at least one satoshi`() {
        val accountId = insertAccount()

        assertThatThrownBy { insertInvestment(accountId, btcAmountSats = 0) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThat(insertInvestment(accountId, btcAmountSats = 1)).isEqualTo(1)
    }

    @Test
    fun `fifo sequence exists and increases`() {
        val first = jdbcTemplate.queryForObject("SELECT nextval('investments_fifo_seq')", Long::class.java)!!
        val second = jdbcTemplate.queryForObject("SELECT nextval('investments_fifo_seq')", Long::class.java)!!

        assertThat(second).isGreaterThan(first)
    }

    @Test
    fun `quote history slot is the primary key`() {
        val insert = {
            jdbcTemplate.update(
                "INSERT INTO quote_history (slot_at, buy_price, sell_price, captured_at) VALUES (?, 1, 1, ?)",
                now,
                now,
            )
        }
        insert()

        assertThatThrownBy { insert() }.isInstanceOf(DuplicateKeyException::class.java)
        val primaryKey =
            jdbcTemplate.queryForList(
                """
                SELECT a.attname FROM pg_index i
                JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
                WHERE i.indrelid = 'quote_history'::regclass AND i.indisprimary
                """.trimIndent(),
                String::class.java,
            )
        assertThat(primaryKey).containsExactly("slot_at")
    }

    private fun insertAccount(): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO accounts (id, name, email, password_hash, balance, created_at) VALUES (?, ?, ?, ?, 0, ?)",
            id,
            "Ana",
            "ana-$id@example.com",
            "hash",
            now,
        )
        return id
    }

    private fun insertInvestment(
        accountId: UUID,
        btcAmountSats: Long,
    ): Int =
        jdbcTemplate.update(
            """
            INSERT INTO investments (id, account_id, purchased_at, fifo_sequence, invested_amount, purchase_quote,
                                     btc_amount_sats, status, origin)
            VALUES (?, ?, ?, nextval('investments_fifo_seq'), 100.00, 400000.00, ?, 'ACTIVE', 'PURCHASE')
            """.trimIndent(),
            UUID.randomUUID(),
            accountId,
            now,
            btcAmountSats,
        )
}
