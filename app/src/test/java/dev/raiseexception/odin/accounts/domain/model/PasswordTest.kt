package dev.raiseexception.odin.accounts.domain.model

import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.shared.domain.Outcome
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MAX_PASSWORD_LENGTH = 100
private const val OVER_MAX_PASSWORD_LENGTH = 101

class PasswordTest {

    @Test
    fun `given a valid password of 12 characters, when creating, then returns success`() {
        val result = Password.create("123456789012".toCharArray())

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a valid password of 100 characters, when creating, then returns success`() {
        val raw = "a".repeat(MAX_PASSWORD_LENGTH).toCharArray()

        val result = Password.create(raw)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a valid password between 12 and 100 characters, when creating, then returns success`() {
        val result = Password.create("mySecurePassword123".toCharArray())

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a valid password with special characters, when creating, then returns success`() {
        val result = Password.create("p@ss!w0rd#\$%^".toCharArray())

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a password shorter than 12 characters, when creating, then returns failure`() {
        val result = Password.create("short".toCharArray())

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }

    @Test
    fun `given an empty password, when creating, then returns failure`() {
        val result = Password.create(charArrayOf())

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }

    @Test
    fun `given a password longer than 100 characters, when creating, then returns failure`() {
        val raw = "a".repeat(OVER_MAX_PASSWORD_LENGTH).toCharArray()

        val result = Password.create(raw)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }
}
