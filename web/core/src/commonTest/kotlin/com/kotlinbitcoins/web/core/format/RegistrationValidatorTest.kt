package com.kotlinbitcoins.web.core.format

import com.kotlinbitcoins.web.core.error.Outcome
import com.kotlinbitcoins.web.core.format.RegistrationError.EmailInvalid
import com.kotlinbitcoins.web.core.format.RegistrationError.NameBlank
import com.kotlinbitcoins.web.core.format.RegistrationError.NameTooLong
import com.kotlinbitcoins.web.core.format.RegistrationError.PasswordTooLong
import com.kotlinbitcoins.web.core.format.RegistrationError.PasswordTooShort
import com.kotlinbitcoins.web.core.support.failureError
import com.kotlinbitcoins.web.core.support.successValue
import kotlin.test.Test
import kotlin.test.assertEquals

/** Local validation of the registration form (FR-001), aligned with Account, Email and PasswordPolicy of the 001. */
class RegistrationValidatorTest {
    private val validName = "Ana Souza"
    private val validEmail = "ana@example.com"
    private val validPassword = "s3nha-forte"

    /** 4 bytes in UTF-8 and 2 UTF-16 units. */
    private val emoji = "😀"

    private fun errors(
        name: String = validName,
        email: String = validEmail,
        password: String = validPassword,
    ): List<RegistrationError> = RegistrationValidator.validate(name, email, password).failureError()

    private fun isValid(
        name: String = validName,
        email: String = validEmail,
        password: String = validPassword,
    ): Boolean = RegistrationValidator.validate(name, email, password) is Outcome.Success

    @Test
    fun `valid input keeps the trimmed name and email and the password as typed`() {
        val valid = RegistrationValidator.validate("  Ana Souza  ", " ana@example.com ", " s3nha forte ").successValue()
        assertEquals("Ana Souza", valid.name)
        assertEquals("ana@example.com", valid.email)
        assertEquals(" s3nha forte ", valid.password)
    }

    @Test
    fun `name is required, ignoring spaces at the ends`() {
        assertEquals(listOf<RegistrationError>(NameBlank), errors(name = ""))
        assertEquals(listOf<RegistrationError>(NameBlank), errors(name = "   "))
    }

    @Test
    fun `name has at most 200 UTF-16 units after trim`() {
        assertEquals(true, isValid(name = "a".repeat(200)))
        assertEquals(true, isValid(name = "  " + "a".repeat(200) + "  "))
        assertEquals(listOf<RegistrationError>(NameTooLong), errors(name = "a".repeat(201)))
        assertEquals(true, isValid(name = emoji.repeat(100)), "100 emojis = 200 units")
        assertEquals(listOf<RegistrationError>(NameTooLong), errors(name = emoji.repeat(101)))
    }

    @Test
    fun `email must have the format of the 001`() {
        listOf("ana", "ana@", "ana@example", "@example.com", "ana @example.com", "ana@exa mple.com", "")
            .forEach { assertEquals(listOf<RegistrationError>(EmailInvalid), errors(email = it), "\"$it\"") }
        assertEquals(true, isValid(email = "ANA@Example.COM"))
    }

    @Test
    fun `email has at most 320 characters`() {
        val domain = "@example.com"
        assertEquals(true, isValid(email = "a".repeat(320 - domain.length) + domain))
        assertEquals(listOf<RegistrationError>(EmailInvalid), errors(email = "a".repeat(321 - domain.length) + domain))
    }

    @Test
    fun `password has at least 8 characters counted as code points`() {
        assertEquals(listOf<RegistrationError>(PasswordTooShort), errors(password = "1234567"))
        assertEquals(listOf<RegistrationError>(PasswordTooShort), errors(password = ""))
        assertEquals(true, isValid(password = "12345678"))
        assertEquals(listOf<RegistrationError>(PasswordTooShort), errors(password = emoji.repeat(7)), "7 code points")
        assertEquals(true, isValid(password = emoji.repeat(8)), "8 code points = 32 bytes")
    }

    @Test
    fun `password has at most 72 bytes in UTF-8`() {
        assertEquals(true, isValid(password = "a".repeat(72)))
        assertEquals(listOf<RegistrationError>(PasswordTooLong), errors(password = "a".repeat(73)))
        assertEquals(listOf<RegistrationError>(PasswordTooLong), errors(password = emoji.repeat(19)), "76 bytes")
        assertEquals(true, isValid(password = emoji.repeat(18)), "72 bytes")
    }

    @Test
    fun `every error at once, in the order name, email, password`() {
        val all = errors(name = " ", email = "ana-sem-arroba", password = "123")
        assertEquals(listOf(NameBlank, EmailInvalid, PasswordTooShort), all)
        assertEquals(
            listOf(RegistrationField.Name, RegistrationField.Email, RegistrationField.Password),
            all.map { it.field },
        )
    }
}
