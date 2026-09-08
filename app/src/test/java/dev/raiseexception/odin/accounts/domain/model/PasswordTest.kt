package dev.raiseexception.odin.accounts.domain.model

import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.crypto.domain.SensitivePassword
import dev.raiseexception.odin.shared.domain.Outcome
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MAX_PASSWORD_LENGTH = 100
private const val OVER_MAX_PASSWORD_LENGTH = 101

class PasswordTest {

    @Test
    fun `given a valid password of 12 characters, when validating, then returns success`() {
        val result = validatePassword(SensitivePassword("123456789012".toCharArray()))

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a valid password of 100 characters, when validating, then returns success`() {
        val raw = "a".repeat(MAX_PASSWORD_LENGTH)

        val result = validatePassword(SensitivePassword(raw.toCharArray()))

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a valid password between 12 and 100 characters, when validating, then returns success`() {
        val result = validatePassword(SensitivePassword("mySecurePassword123".toCharArray()))

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a valid password with special characters, when validating, then returns success`() {
        val result = validatePassword(SensitivePassword("p@ss!w0rd#\$%^".toCharArray()))

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a password shorter than 12 characters, when validating, then returns failure`() {
        val result = validatePassword(SensitivePassword("short".toCharArray()))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }

    @Test
    fun `given an empty password, when validating, then returns failure`() {
        val result = validatePassword(SensitivePassword(charArrayOf()))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }

    @Test
    fun `given a password longer than 100 characters, when validating, then returns failure`() {
        val raw = "a".repeat(OVER_MAX_PASSWORD_LENGTH)

        val result = validatePassword(SensitivePassword(raw.toCharArray()))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }
}
